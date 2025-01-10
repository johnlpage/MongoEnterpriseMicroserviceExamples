package com.johnlpage.mews.repository;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.johnlpage.mews.models.MewsModel;
import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

@Repository
public class OptimizedMongoLoadRepositoryImpl<T extends MewsModel<ID>, ID>
    implements OptimizedMongoLoadRepository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizedMongoLoadRepositoryImpl.class);
  private final AtomicInteger updates = new AtomicInteger(0);
  private final AtomicInteger deletes = new AtomicInteger(0);
  private final AtomicInteger inserts = new AtomicInteger(0);
  private final MongoTemplate mongoTemplate;
  private final MappingMongoConverter mappingMongoConverter;

  @Autowired
  public OptimizedMongoLoadRepositoryImpl(
      MongoTemplate mongoTemplate, MappingMongoConverter mappingMongoConverter) {
    this.mongoTemplate = mongoTemplate;
    this.mappingMongoConverter = mappingMongoConverter;
  }

  @Override
  public BulkWriteResult writeMany(List<T> items, Class<T> clazz) {
    return writeMany(items, clazz, false);
  }

  // Let's not reply on relection more than we need to.

  @Async("loadExecutor")
  public void asyncWriteMany(List<T> toSave, Class<T> clazz) {
    asyncWriteMany(toSave, clazz, false);
  }

  @Override
  public BulkWriteResult writeMany(List<T> items, Class<T> clazz, boolean useUpdateNotReplace) {
    BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);

    for (T item : items) {

      Query query = new Query(where("_id").is(item.getDocumentId()));

      if (item.toDelete()) {
        ops.remove(query);
      } else {
        // Get the BSON Document from the item
        if (useUpdateNotReplace) {
          Document bsonDocument = new Document();
          mappingMongoConverter.write(item, bsonDocument);

          // Create an Update object and add fields to it
          Update update = new Update();
          BuildUpdateFromDocument(bsonDocument, update);

          ops.upsert(query, update);
        } else {
          // Replace is less network/storage/backup efficient as whole document is
          // replicated.
          ops.replaceOne(query, item, FindAndReplaceOptions.options().upsert());
        }
      }
    }
    return ops.execute();
  }

  // Recurse through A document turning each individual singleton field (not
  // arrays) into a
  // value in $set - i.e. { $set : { a:1, o.b: 2, o.c: 3 }}

  @Async("loadExecutor")
  public void asyncWriteMany(List<T> toSave, Class<T> clazz, boolean useUpdateNotReplace) {
    try {
      // Update some thread safe counts for upserts, deletes and modifications.
      BulkWriteResult r = writeMany(toSave, clazz, useUpdateNotReplace);
      updates.addAndGet(r.getModifiedCount());
      deletes.addAndGet(r.getDeletedCount());
      inserts.addAndGet(r.getUpserts().size());

    } catch (Exception e) {
      LOG.error(e.getMessage());
      // TODO Handle Failed writes going to a dead letter queue or similar.

    }
  }

  public void resetStats() {
    inserts.set(0);
    updates.set(0);
    deletes.set(0);
  }

  public AtomicInteger getUpdates() {
    return updates;
  }

  public AtomicInteger getDeletes() {
    return deletes;
  }

  public AtomicInteger getInserts() {
    return inserts;
  }

  private void BuildUpdateFromDocument(Document bsonDocument, Update update) {
    BuildUpdateFromDocument(bsonDocument, update, "");
  }

  private void BuildUpdateFromDocument(Document bsonDocument, Update update, String basekey) {
    for (Map.Entry<String, Object> entry : bsonDocument.entrySet()) {
      // If it's a document then recurse
      // Don't recurse into Arrays (It's possible but there are caveats to think
      // about like deletions)
      if (entry.getValue() instanceof Document) {
        BuildUpdateFromDocument(
            (Document) entry.getValue(), update, basekey + entry.getKey() + ".");
      } else {
        update.set(basekey + entry.getKey(), entry.getValue());
      }
    }
  }
}
