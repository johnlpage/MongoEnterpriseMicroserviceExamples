package com.johnlpage.mews.repository;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.johnlpage.mews.models.MEWSModel;
import com.mongodb.bulk.BulkWriteResult;

public interface OptimizedMongoLoadRepository<T extends MEWSModel> {
    BulkWriteResult writeMany(List<T> items);
    void asyncWriteMany(List<T> items);
    BulkWriteResult writeMany(List<T> items, boolean useUpdateNotReplace);
    void asyncWriteMany(List<T> items,  boolean useUpdateNotReplace);
    void resetStats();
    AtomicInteger getUpdates();
    AtomicInteger getDeletes();
    AtomicInteger getInserts();
    

}
