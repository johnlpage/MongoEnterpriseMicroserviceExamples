package com.johnlpage.memex.repository.optimized;

import com.johnlpage.memex.model.UpdateStrategy;
import com.johnlpage.memex.service.generic.InvalidDataHandlerService;
import com.johnlpage.memex.service.generic.PostWriteTriggerService;
import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface OptimizedMongoLoadRepository<T> {

  BulkWriteResult writeMany(
      List<T> items,
      Class<T> clazz,
      InvalidDataHandlerService<T> invalidDataHandlerService,
      UpdateStrategy updateStrategy,
      PostWriteTriggerService<T> postTrigger)
      throws IllegalAccessException;

  CompletableFuture<BulkWriteResult> asyncWriteMany(
      List<T> items,
      Class<T> clazz,
      InvalidDataHandlerService<T> invalidDataHandlerService,
      UpdateStrategy updateStrategy,
      PostWriteTriggerService<T> postTrigger);
}
