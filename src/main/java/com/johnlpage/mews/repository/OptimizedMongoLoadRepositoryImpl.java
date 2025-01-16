package com.johnlpage.mews.repository;

import static com.johnlpage.mews.util.AnnotationExtractor.getIdFromModel;
import static com.johnlpage.mews.util.AnnotationExtractor.hasDeleteFlag;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.johnlpage.mews.model.UpdateStrategy;
import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;

@RequiredArgsConstructor
public class OptimizedMongoLoadRepositoryImpl<T , ID>
    implements OptimizedMongoLoadRepository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizedMongoLoadRepositoryImpl.class);
  private final MongoTemplate mongoTemplate;
  private final MappingMongoConverter mappingMongoConverter;

  @Override
  public BulkWriteResult writeMany(List<T> items, Class<T> clazz) throws IllegalAccessException {
    return writeMany(items, clazz, UpdateStrategy.REPLACE);
  }

  private void BuildUpdateFromDocument(Document bsonDocument, Update update) {
    BuildUpdateFromDocument(bsonDocument, update, "");
  }

  /**
   *   Recurse through A document turning each individual singleton field (not
   *   arrays) into a
   *   value in $set - i.e. { $set : { a:1, o.b: 2, o.c: 3 }}
   */

  private void BuildUpdateFromDocument(Document bsonDocument, Update update, String basekey) {
    for (Map.Entry<String, Object> entry : bsonDocument.entrySet()) {
      // If it's a document then recurse
      // Don't recurse into Arrays (It's possible but there are catveates to think
      // about like deletions)
      if (entry.getValue() instanceof Document) {
        BuildUpdateFromDocument(
                (Document) entry.getValue(), update, basekey + entry.getKey() + ".");
      } else {
        update.set(basekey + entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  public BulkWriteResult writeMany(List<T> items, Class<T> clazz, UpdateStrategy updateStrategy) throws IllegalAccessException {
    BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);

    for (T item : items) {

      Object idValue = getIdFromModel(item);
      Query query = new Query(where("_id").is(idValue));

      if (hasDeleteFlag(item)) {
        ops.remove(query);
      } else {
        // Get the BSON Document from the item
        if (updateStrategy == UpdateStrategy.UPDATE) {
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
