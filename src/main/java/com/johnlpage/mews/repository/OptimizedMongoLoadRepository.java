package com.johnlpage.mews.repository;

import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public interface OptimizedMongoLoadRepository<T> {
  BulkWriteResult writeMany(List<T> items, Class<T> clazz);

  void asyncWriteMany(List<T> items, Class<T> clazz);

  BulkWriteResult writeMany(List<T> items, Class<T> clazz, boolean useUpdateNotReplace);

  void asyncWriteMany(List<T> items, Class<T> clazz, boolean useUpdateNotReplace);

  void resetStats();

  AtomicInteger getUpdates();

  AtomicInteger getDeletes();

  AtomicInteger getInserts();
}
