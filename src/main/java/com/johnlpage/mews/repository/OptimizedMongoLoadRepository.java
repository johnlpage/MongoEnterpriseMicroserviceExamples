package com.johnlpage.mews.repository;

import com.johnlpage.mews.model.UpdateStrategy;
import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface OptimizedMongoLoadRepository<T> {
  BulkWriteResult writeMany(List<T> items, Class<T> clazz) throws IllegalAccessException;

  CompletableFuture<BulkWriteResult> asyncWriteMany(List<T> items, Class<T> clazz);

  BulkWriteResult writeMany(List<T> items, Class<T> clazz, UpdateStrategy updateStrategy) throws IllegalAccessException;

  CompletableFuture<BulkWriteResult> asyncWriteMany(
      List<T> items, Class<T> clazz, UpdateStrategy updateStrategy);
}
