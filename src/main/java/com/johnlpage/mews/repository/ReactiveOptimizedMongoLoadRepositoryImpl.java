package com.johnlpage.mews.repository;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.johnlpage.mews.models.MewsModel;
import com.johnlpage.mews.models.UpdateStrategy;
import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.ReactiveBulkOperations;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Repository
public class ReactiveOptimizedMongoLoadRepositoryImpl<T extends MewsModel<ID>, ID>
    implements ReactiveOptimizedMongoLoadRepository<T> {

  private final ReactiveMongoTemplate reactiveMongoTemplate;
  private final MappingMongoConverter mappingMongoConverter;

  @Override
  public Mono<BulkWriteResult> bulkWrite(List<T> items, Class<T> clazz) {
    return bulkWrite(items, clazz, UpdateStrategy.REPLACE);
  }

  @Override
  public Mono<BulkWriteResult> bulkWrite(
      List<T> items, Class<T> clazz, UpdateStrategy updateStrategy) {
    ReactiveBulkOperations ops = reactiveMongoTemplate.bulkOps(BulkMode.UNORDERED, clazz);
    for (T item : items) {
      Query query = new Query(where("_id").is(item.getDocumentId()));
      if (item.toDelete()) {
        ops.remove(query);
        continue;
      }
      // Get the BSON Document from the item
      if (updateStrategy == UpdateStrategy.UPSERT) {
        Document bsonDocument = new Document();
        mappingMongoConverter.write(item, bsonDocument);
        // Create an Update object and add fields to it
        Update update = Update.fromDocument(bsonDocument);
        ops.upsert(query, update);
      } else {
        // Replace is less network/storage/backup efficient as whole document is replicated.
        ops.replaceOne(query, item, FindAndReplaceOptions.options().upsert());
      }
    }
    return ops.execute();
  }
}
