package com.johnlpage.memex.repository.optimized;

import static com.johnlpage.memex.util.AnnotationExtractor.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.johnlpage.memex.model.UpdateStrategy;
import com.johnlpage.memex.service.generic.PostWriteTriggerService;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class OptimizedMongoLoadRepositoryImpl<T> implements OptimizedMongoLoadRepository<T> {

  // Used in trigger definitions
  public static final String PREVIOUS_VALS = "__previousValues";
  public static final String UPDATE_ID = "__updateId";
  public static final String LAST_UPDATE_DATE = "__lastUpdateDate";
  private static final Logger LOG = LoggerFactory.getLogger(OptimizedMongoLoadRepositoryImpl.class);
  // Internal only
  private static final String BACKUP_VALS = "__backupValues";
  private static final String CHANGED = "__changed";
  private static final String IS_INSERT = "__isInsert";
  private static final Document flagInsert;
  private static final Document backupDelta;
  private static final Document cleanUp;

  static {
    Document previousSize = new Document("$size", new Document("$objectToArray", "$$ROOT"));
    Document isInsert = new Document("$eq", Arrays.asList(previousSize, 1));
    flagInsert = new Document("$set", new Document(IS_INSERT, isInsert));

    backupDelta =
        new Document(
            "$set",
            new Document(PREVIOUS_VALS, new Document()).append(BACKUP_VALS, "$" + PREVIOUS_VALS));

    // If there was no change then revert to BACKUP_VALS
    Document finalUpdate =
        new Document("$cond", Arrays.asList("$" + CHANGED, "$" + PREVIOUS_VALS, "$" + BACKUP_VALS));
    // For an insert all we want it the timestamp
    Document condFinal =
        new Document(
            "$cond",
            Arrays.asList("$" + IS_INSERT, new Document(LAST_UPDATE_DATE, "$$NOW"), finalUpdate));
    cleanUp =
        new Document(
            "$set",
            new Document(BACKUP_VALS, "$$REMOVE")
                .append(CHANGED, "$$REMOVE")
                .append(IS_INSERT, "$$REMOVE")
                .append(PREVIOUS_VALS, condFinal));
  }

  private final MongoTemplate mongoTemplate;
  private final MappingMongoConverter mappingMongoConverter;
  private final MongoClient mongoClient;

  public BulkWriteResult writeMany(
      List<T> items,
      Class<T> clazz,
      UpdateStrategy updateStrategy,
      PostWriteTriggerService<T> postWrite)
      throws IllegalAccessException {

    ClientSession session = null;
    ObjectId updateBatchId = new ObjectId();
    boolean usingTransactions = postWrite != null;

    BulkOperations ops;
    if (usingTransactions) {
      session = mongoClient.startSession();
      session.startTransaction();
      ops = mongoTemplate.withSession(session).bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);
    } else {
      ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);
    }

    for (T item : items) {
      Object idValue = getIdFromModel(item);
      Query query = new Query(where("_id").is(idValue));
      if (hasDeleteFlag(item)) {

        /* TODO - Figure out history on this, when we delete one we need to keep it in history
        For now we are auditing a delete op and removing the top level document but keeping the last
        version in history. We could alternatively purge all from history too */

        ops.remove(query);

      } else {
        if (updateStrategy == UpdateStrategy.INSERT) {
          // If a @Version annotation is in the model then make sure we set it to 1
          Field versionField = getVersionField(item);
          if (versionField != null) {
            versionField.set(item, 1); // On insert set the versions to 1
          }
          ops.insert(item); // This will throw exceptions on duplicates
        } else if (updateStrategy == UpdateStrategy.UPDATE) {
          // TODO - Version field ?
          useSimpleUpdate(item, ops, query); // Unwinds and uses $set - smaller oplog, less network

        } else if (updateStrategy == UpdateStrategy.UPDATEWITHHISTORY) {
          // TODO - Version field
          useSmartUpdate(item, ops, query, updateBatchId);
        } else {
          // Basic overwrite, can be a little less CPU but more network/disk
          // Still better than Spring's default

          // TODO - Version field ?
          ops.replaceOne(query, item, FindAndReplaceOptions.options().upsert());
        }
      }
    }

    // If we have a postWriteTrigger then we want this update to be
    // In a transaction if not we don't
    try {
      BulkWriteResult result = ops.execute();
      if (usingTransactions) {
        postWrite.postWriteTrigger(session, result, items, clazz, updateBatchId);
        session.commitTransaction();
      }
      return result;
    } catch (Exception e) {
      // TODO - Add code to retry if a transient transaction error, that would happen if
      // two threads had updates to the same document and means retrying the whole set
      // Only happens if playing a bunch of updates with multiple updates to same doc in different
      // simultaneous batches Ties into dead letter queue etc.

      LOG.error(e.getMessage());
      if (usingTransactions && session.hasActiveTransaction()) {
        session.abortTransaction();
      }
      throw e; // Rethrow to handle upstream
    }
  }

  /**
   * Change to using a Pipeline, multistep update If we are blindly saving incoming data we don't
   * know what has changed either at a field or document level. This type of update will let us
   * efficiently capture that
   *
   * <p>(a) Record a flag to say at least one field has changed. (b) Record set of fields that
   * changed and their prior values (c) Optionally change this to keep a history of all changes in
   * the record but that's a different strategy.
   *
   * <p>We can combine this with a transaction and a query to fetch just the updated documents for
   * various post-update transactional trigger activities.
   */
  private void useSmartUpdate(T item, BulkOperations ops, Query query, ObjectId updateBatchId) {
    // Generate a Mongo Document with all the required fields in and all the mappings applied
    // Was using various Spring builders for this, but it was a lot slower and more CPU to build
    // them  programmatically with the fluent builders as they weren't designed for that .
    Document bsonDocument = new Document();
    mappingMongoConverter.write(item, bsonDocument);

    // Compute all the individual scalar values that have changed.
    // Arrays are just treated as scalars for now
    // Unwinding arrays is possible using a.1.b a.2.b syntax however if we then use just update
    // a.1.b becomes  { a: { 1 : {b : "X"}} not { a:[null,{b:b}]} - to fix that we need to move to a
    // piplined update and in that we cannot use dot paths - this needs more thought.

    Map<String, Object> unwoundFields = new HashMap<>();
    unwindNestedDocumentsInUpdate(bsonDocument, unwoundFields);

    // Iterate over the map and modify the string values starting with $
    //  As they will be in interpreted as variables to Document("$literal","$thing")

    for (Map.Entry<String, Object> entry : unwoundFields.entrySet()) {
      if (entry.getValue() instanceof String value) {
        if (value.startsWith("$")) {
          // Prefix with _ if it starts with $
          entry.setValue(new Document("$literal", value));
        }
      }
    }

    List<Document> updateSteps = new ArrayList<>();

    // If this is an insert then $_id will be undefined, in that case we don't need a previous
    // version
    // Worst case if we didn't do this we would have a lot of superfluous history.
    // Detecting an insert when upserting is tricky as _id is already populated but nothing else

    // Defined statically
    updateSteps.add(flagInsert);
    updateSteps.add(backupDelta);

    Document previousValues = new Document(PREVIOUS_VALS + "." + UPDATE_ID, updateBatchId);
    previousValues.put(PREVIOUS_VALS + "." + LAST_UPDATE_DATE, "$$NOW");

    List<Document> anyChange = new ArrayList<>();
    for (Map.Entry<String, Object> entry : unwoundFields.entrySet()) {
      // True if the value has changed

      Document valueChanged =
          new Document("$ne", Arrays.asList("$" + entry.getKey(), entry.getValue()));
      // If changed record old value otherwise record nothing
      Document coerceEmptyToNull =
          new Document("$ifNull", Arrays.asList("$" + entry.getKey(), null));
      Document conditionalOnChange =
          new Document("$cond", Arrays.asList(valueChanged, coerceEmptyToNull, "$$REMOVE"));

      previousValues.append(PREVIOUS_VALS + "." + entry.getKey(), conditionalOnChange);
      // List of all the conditionals so we can work out if anything changed
      anyChange.add(valueChanged);
    }

    // If any changed set flag to true
    previousValues.append(CHANGED, new Document("$or", anyChange));
    updateSteps.add(new Document("$set", previousValues));

    // Set to new values
    updateSteps.add(new Document("$set", unwoundFields));

    // Don't need our backup copy anymore
    updateSteps.add(cleanUp);

    // Uncomment to see The Pipeline Query, do so with a very simple document
    for (Document s : updateSteps) {
      LOG.info(s.toJson());
    }

    // Because these expressive pipeline updates are using pipelines they are sometimes
    // Referred to as Aggregation Updates, that's the name of the Spring Data MongoDB class
    AggregationUpdate aggUpdate =
        AggregationUpdate.from(
            updateSteps.stream()
                .map(stage -> (AggregationOperation) context -> stage)
                .collect(Collectors.toList()));

    ops.upsert(query, aggUpdate);
  }

  /**
   * Without a postTrigger this is non-transactional - not currently in use, but you may not want a
   * trigger
   */
  @Async("loadExecutor")
  public CompletableFuture<BulkWriteResult> asyncWriteMany(
      List<T> toSave, Class<T> clazz, UpdateStrategy updateStrategy) {
    try {
      // Update some thread safe counts for upserts, deletes and modifications.
      return CompletableFuture.completedFuture(writeMany(toSave, clazz, updateStrategy, null));
    } catch (Exception e) {
      LOG.error(e.getMessage());
      // TODO Consider Failed writes going to a dead letter queue or similar.
      // That may be something you do from the CompletableFuture
      return CompletableFuture.failedFuture(e);
    }
  }

  @Async("loadExecutor")
  public CompletableFuture<BulkWriteResult> asyncWriteMany(
      List<T> toSave,
      Class<T> clazz,
      UpdateStrategy updateStrategy,
      PostWriteTriggerService<T> postTrigger) {
    try {
      // Update some thread safe counts for upserts, deletes and modifications.
      return CompletableFuture.completedFuture(
          writeMany(toSave, clazz, updateStrategy, postTrigger));
    } catch (Exception e) {
      LOG.error(e.getMessage());
      // TODO Consider Failed writes going to a dead letter queue or similar.
      // That may be something you do from the CompletableFuture
      return CompletableFuture.failedFuture(e);
    }
  }

  private void useSimpleUpdate(T item, BulkOperations ops, Query query) {
    Document bsonDocument = new Document();
    mappingMongoConverter.write(item, bsonDocument);

    // Compute all the individual scalar values that have changed.
    // Arrays are just treated as scalars for now - complex topic.

    Document unwoundFields = new Document();
    unwindNestedDocumentsInUpdate(bsonDocument, unwoundFields);

    Update update = new Update();
    // Create an Update object
    // Apply all entries from the Map to the Update object
    unwoundFields.forEach(update::set);
    ops.upsert(query, update);
  }

  /**
   * This makes all nested, non array fields into individual paths so they can be considered and set
   * independently - in a simple case { a: 1, b: { c:2, d:3}} --> { a:1, "b.c":2, "b.d":3 } We use
   * this to get the list of field paths we are updating and MongoDB can then diff them individually
   * internally to calculate minimum change.
   */
  private void unwindNestedDocumentsInUpdate(
      Map<String, Object> in, Map<String, Object> out, String basekey) {
    if (out == null || in == null) return;

    for (Map.Entry<String, Object> entry : in.entrySet()) {
      // If it's a document then recurse
      // Don't recurse into Arrays (It's possible but there are caveats to think
      // about like deletions)
      if (entry.getValue() instanceof Document) {
        unwindNestedDocumentsInUpdate(
            (Document) entry.getValue(), out, basekey + entry.getKey() + ".");
      } else {
        if (!(basekey.isEmpty() && entry.getKey().equals("_id"))) {
          out.put(basekey + entry.getKey(), entry.getValue());
        }
      }
    }
  }

  private void unwindNestedDocumentsInUpdate(Map<String, Object> in, Map<String, Object> out) {
    unwindNestedDocumentsInUpdate(in, out, "");
  }
}
