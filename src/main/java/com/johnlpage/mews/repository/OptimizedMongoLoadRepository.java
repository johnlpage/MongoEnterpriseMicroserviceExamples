package com.johnlpage.mews.repository;

import com.johnlpage.mews.models.MewsModel;
import com.mongodb.bulk.BulkWriteResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public interface OptimizedMongoLoadRepository<T extends MewsModel> {
    BulkWriteResult writeMany(List<T> items);

    void asyncWriteMany(List<T> items);

    BulkWriteResult writeMany(List<T> items, boolean useUpdateNotReplace);

    void asyncWriteMany(List<T> items, boolean useUpdateNotReplace);

    void resetStats();

    AtomicInteger getUpdates();

    AtomicInteger getDeletes();

    AtomicInteger getInserts();

}
