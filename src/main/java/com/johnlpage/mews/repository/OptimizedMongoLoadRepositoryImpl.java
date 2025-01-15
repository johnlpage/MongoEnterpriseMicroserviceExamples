package com.johnlpage.mews.repository;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.johnlpage.mews.model.Deleteable;
import com.johnlpage.mews.model.UpdateStrategy;
import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
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
public class OptimizedMongoLoadRepositoryImpl<T extends Deleteable<ID>, ID>
    implements OptimizedMongoLoadRepository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizedMongoLoadRepositoryImpl.class);
  private final MongoTemplate mongoTemplate;
  private final MappingMongoConverter mappingMongoConverter;

  @Override
  public BulkWriteResult writeMany(List<T> items, Class<T> clazz) {
    return writeMany(items, clazz, UpdateStrategy.REPLACE);
  }

  @Override
  public BulkWriteResult writeMany(List<T> items, Class<T> clazz, UpdateStrategy updateStrategy) {
    BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);

    for (T item : items) {

      Query query = new Query(where("_id").is(item.getDocumentId()));

      if (item.toDelete()) {
        ops.remove(query);
      } else {
        // Get the BSON Document from the item
        if (updateStrategy == UpdateStrategy.UPSERT) {
          Document bsonDocument = new Document();
          mappingMongoConverter.write(item, bsonDocument);

          // Create an Update object and add fields to it
          Update update = Update.fromDocument(bsonDocument);

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
