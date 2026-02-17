package com.johnlpage.memex.generics.repository;

import com.johnlpage.memex.util.UpdateStrategy;
import com.johnlpage.memex.generics.service.InvalidDataHandlerService;
import com.johnlpage.memex.generics.service.PostWriteTriggerService;
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
