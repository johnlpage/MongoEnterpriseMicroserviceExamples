package com.johnlpage.mews.repository;

import static com.johnlpage.mews.util.AnnotationExtractor.getIdFromModel;
import static com.johnlpage.mews.util.AnnotationExtractor.hasDeleteFlag;
import static org.springframework.data.mongodb.core.aggregation.ConditionalOperators.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.johnlpage.mews.model.UpdateStrategy;
import com.mongodb.bulk.BulkWriteResult;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
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
import org.springframework.data.mongodb.util.aggregation.TestAggregationContext;
import org.springframework.scheduling.annotation.Async;

@RequiredArgsConstructor
public class OptimizedMongoLoadRepositoryImpl<T, ID> implements OptimizedMongoLoadRepository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizedMongoLoadRepositoryImpl.class);
  private final MongoTemplate mongoTemplate;
  private final MappingMongoConverter mappingMongoConverter;

  @Override
  public BulkWriteResult writeMany(List<T> items, Class<T> clazz) throws IllegalAccessException {
    return writeMany(items, clazz, UpdateStrategy.REPLACE);
  }

  /**
   * This builds the update so it records any fields what have changed since the last update And
   * their previous values, it also sets a flag if and only if the record would otherwise have
   * changed allowing us to retrieve all the modifued records and their histories this can be
   * thought of as a transactional alternative to a change stream. And unlike findOneAndUpdate works
   * in batches.
   */
  private void BuildUpdateWithHistoryFromDocument(
      Document bsonDocument, Update update, String basekey) {}

  /**
   * This makes all nested, non array fields (arrays are a todo) into individual paths so they can
   * be considered and set independently - in a simple case { a: 1, b: { c:2, d:3}} --> { a:1,
   * "b.c":2, "b.d":3 } We use this to get the list of field paths we are updating and MongoDB can
   * then diff them individually internally to calculate minimum change.
   */
  private void unwindNestedDocumentsInUpdate(
      Map<String, Object> in, Map<String, Object> out, String basekey) {
    if (out == null || in == null) return;

    for (Map.Entry<String, Object> entry : in.entrySet()) {
      // If it's a document then recurse
      // Don't recurse into Arrays (It's possible but there are catveates to think
      // about like deletions)
      if (entry.getValue() instanceof Document) {
        unwindNestedDocumentsInUpdate(
            (Document) entry.getValue(), out, basekey + entry.getKey() + ".");
      } else {
        out.put(basekey + entry.getKey(), entry.getValue());
      }
    }
  }

  private void unwindNestedDocumentsInUpdate(Map<String, Object> in, Map<String, Object> out) {
    unwindNestedDocumentsInUpdate(in, out, "");
  }

  @Override
  public BulkWriteResult writeMany(List<T> items, Class<T> clazz, UpdateStrategy updateStrategy)
      throws IllegalAccessException {
    BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);
    ObjectId updateBatchId = new ObjectId();

    for (T item : items) {

      Object idValue = getIdFromModel(item);
      Query query = new Query(where("_id").is(idValue));

      if (hasDeleteFlag(item)) {
        ops.remove(query);
      } else {
        if (updateStrategy == UpdateStrategy.UPDATE) {
          useSimpleUpdate(item, ops, query); // Unwinds and uses $set - smaller oplog, less network
        } else if (updateStrategy == UpdateStrategy.UPDATEWITHHISTORY) {
          useSmartUpdate(item, ops, query, updateBatchId);
        } else {
          // Basic overwrite, can be a little less CPU but more network/disk
          // Still better than Spring's default
          ops.replaceOne(query, item, FindAndReplaceOptions.options().upsert());
        }
      }
    }
    return ops.execute();
  }

  private void useSimpleUpdate(T item, BulkOperations ops, Query query) {
    Document bsonDocument = new Document();
    mappingMongoConverter.write(item, bsonDocument);

    // Compute all the individual scalar values that havee changed.
    // Arrays are just treated as scalars for now (todo)

    Document unwoundFields = new Document();
    unwindNestedDocumentsInUpdate(bsonDocument, unwoundFields);

    Update update = new Update();
    // Create an Update object
    // Apply all entries from the Map to the Update object
    unwoundFields.forEach(update::set);
    ops.upsert(query, update);
  }

  /**
   * Change to using a Pipeline, multistep update If we are blindly saving incoming data we don't
   * know what has changed either at a field or document level. This type of update will let us
   * efficiently capture that
   *
   * <p>(a) Record a flag to say at least one field has changed. (b)  Record set of fields
   * that changed and their prior values (c) Optionally keep a history of all changes (todo)
   *
   * <p>We can combine this with a transaction and a query to fetch just the updated documents for
   * various post-update transactional trigger activities.
   */

  // TODO - Get Spring Builders to work for this, see below

  private void useSmartUpdate(T item, BulkOperations ops, Query query, ObjectId updateBatchId) {
    //User in debug logging
    AggregationOperationContext context = TestAggregationContext.contextFor(item.getClass());

    // Generate a Mongo Document with all the required fields in and all the mappings applied

    Document bsonDocument = new Document();
    mappingMongoConverter.write(item, bsonDocument);

    // Compute all the individual scalar values that have changed.
    // Arrays are just treated as scalars for now (todo - Unwind arrays too, IMPORTANT)
    Map<String, Object> unwoundFields = new HashMap<String, Object>();
    unwindNestedDocumentsInUpdate(bsonDocument, unwoundFields);

    // Because these expressive pipeline updates are using pipelines they are sometimes
    // Referred to as Aggregation Updates, that's the name of the Spring Data MongoDB class

    AggregationUpdate update = Aggregation.newUpdate();

    // Step 1  If we have __previousvalues, copy it to _originalDelta and empty __previousvalues
    // we use __previousvalues to record what fields are changing and their prior values
    // JS: { $set: { __previousvalues : { _changed: false } , _originalDelta : "$__previousvalues"}}

    SetOperation backupDelta =
        SetOperation.set("__previousvalues").toValue(new Document("_changed",false)).set("_originalDelta", "$__previousvalues");


    update.set(backupDelta); // Add a stage to the update

    // Step 2 - for each field in the unwound object we need to make three changes.
    // Set __previousvalues.<FNAME> to the old value if it has changed wit an explicit NULL if empty
    // OR __previousvalues._changed with true if it has changed
    // Set the field to the required value

    /*
      valueChanged = { $ne: [val, `$${field}`] }
       ${field} = val
      __previousvalues.${field} = { $cond: [valueChanged, { $ifNull: [`$${field}`, null] }, "$$REMOVE"]}
      __previousvalues._changed = { $or: ["$__previousvalues._changed", valueChanged] }
     */

    for (Map.Entry<String, Object> entry : unwoundFields.entrySet()) {
      String fName = entry.getKey();
      Object newValue = entry.getValue();
      String deltaPath = "__previousvalues." + fName;

      SetOperation changes = new SetOperation(fName,newValue);
      // Criteria doesn't work all the time for hasChanged due to object comparison decomposition
      Document hasChanged = new Document("$ne", Arrays.asList("$" + fName, newValue));
      // If the field doesn't exist I want it in the history as null not non-existent so
      // When applying history I know when to null it our in MDB null == non-existent

      Document explicitNull = new Document("$ifNull", Arrays.asList("$" + fName, null));

      Cond changedValue =
              ConditionalOperators.Cond.when(hasChanged)
                      .then(explicitNull)
                      .otherwise("$$REMOVE");

      Document logicalOrWithChanged = new Document("$or", Arrays.asList("$__previousvalues._changed" + fName, hasChanged));
      changes = changes.set(deltaPath,changedValue).set("__previousvalues._changed",logicalOrWithChanged);
      update.set(changes);
    }
    SetOperation calculateNewDelta = new SetOperation("blank", "$$REMOVE"); // Hack



    // Step 3 - if __previousvalues._changed is false, then no fields are being changed anyway -
    // so put __previousvalues back the way it was using _original__previousvalues
    // if delta HAS values in it then add a unique identifier we can use to find this update

    // Document somethingHasChanged = new Document("$ne", Arrays.asList("$__previousvalues", new Document()));

    // Add in __previousvalues._updateBatchId
    // At this point based on "__previousvalues._changed" you might set a last update date or push the delta into an array
    // If you wanted to push everything into a single server call

    Document lastUpdateId = new Document("_updateBatchId", updateBatchId);
    ObjectOperators.MergeObjects deltaWithUpdateID =
        ObjectOperators.MergeObjects.merge().mergeWithValuesOf("__previousvalues").mergeWith(lastUpdateId);


    SetOperation cancelAllIfNothingChanged = new SetOperation("_originalDelta", "$$REMOVE");
    Cond finalDeltaValue =
        ConditionalOperators.Cond.when("__previousvalues._changed")
            .then(deltaWithUpdateID)
            .otherwiseValueOf("_originalDelta");

    cancelAllIfNothingChanged = cancelAllIfNothingChanged.set("__previousvalues", finalDeltaValue);

    update.set(cancelAllIfNothingChanged);

    //Final clenaup of _changed field
    update.set("__previousvalues._changed").toValue("$$REMOVE");


    /*
    JsonWriterSettings prettyPrintSettings =
        JsonWriterSettings.builder()
            .indent(true) // Enables indentation for pretty printing
            .outputMode(JsonMode.RELAXED) // Use RELAXED mode for extended JSON representation
            .build();

    LOG.info(update.toDocument("inspections", context).toJson(prettyPrintSettings));
    */

    ops.upsert(query, update);
  }

  @Async("loadExecutor")
  public CompletableFuture<BulkWriteResult> asyncWriteMany(List<T> toSave, Class<T> clazz) {
    return asyncWriteMany(toSave, clazz, UpdateStrategy.REPLACE);
  }

  @Async("loadExecutor")
  public CompletableFuture<BulkWriteResult> asyncWriteMany(
      List<T> toSave, Class<T> clazz, UpdateStrategy updateStrategy) {
    try {
      // Update some thread safe counts for upserts, deletes and modifications.
      return CompletableFuture.completedFuture(writeMany(toSave, clazz, updateStrategy));
    } catch (Exception e) {
      LOG.error(e.getMessage());
      // TODO Handle Failed writes going to a dead letter queue or similar.

      return CompletableFuture.failedFuture(e);
    }
  }
}
