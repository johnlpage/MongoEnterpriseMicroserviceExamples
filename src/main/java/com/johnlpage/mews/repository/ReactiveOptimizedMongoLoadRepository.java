package com.johnlpage.mews.repository;

import com.johnlpage.mews.models.UpdateStrategy;
import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveOptimizedMongoLoadRepository<T> {

  Mono<BulkWriteResult> bulkWrite(List<T> items, Class<T> clazz);

  Mono<BulkWriteResult> bulkWrite(List<T> items, Class<T> clazz, UpdateStrategy updateStrategy);
}
