package com.johnlpage.mews.repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;

import com.johnlpage.mews.models.MEWSModel;

import com.mongodb.bulk.BulkWriteResult;

import static org.springframework.data.mongodb.core.query.Criteria.where;

public class OptimizedMongoLoadRepositoryImpl<T extends MEWSModel> implements OptimizedMongoLoadRepository<T> {

    private final AtomicInteger updates = new AtomicInteger(0);
    private final AtomicInteger deletes = new AtomicInteger(0);
    private final AtomicInteger inserts = new AtomicInteger(0);

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(OptimizedMongoLoadRepositoryImpl.class);
    
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private MappingMongoConverter mappingMongoConverter;

    @Override
    public BulkWriteResult writeMany(List<T> items) {
        return  writeMany(items,false);
    }
    // Lets not reply on relection more than we need to.

    @Override
    public BulkWriteResult writeMany(List<T> items, boolean useUpdateNotReplace) {
        BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED,
                OptimizedMongoLoadRepositoryImpl.class);
        for (MEWSModel t : items) {

            Query q = new Query(where("_id").is(t.getDocumentId()));

            if (t.isDeleted()) {
                ops.remove(q);
            } else {
                // Get the BSON Document from the item
                if (useUpdateNotReplace) {
                    Document bsonDocument = new Document();
                    mappingMongoConverter.write(t, bsonDocument);

                    // Create an Update object and add fields to it
                    Update update = new Update();
                    BuildUpdateFromDocument(bsonDocument, update);

                    ops.upsert(q, update);
                } else {
                    // Replace is less network/storage/backup efficient as whole document is
                    // replicated.
                    ops.replaceOne(q, t, FindAndReplaceOptions.options().upsert());
                }
            }
        }
        BulkWriteResult s = ops.execute();
        return s;
    }

    private void BuildUpdateFromDocument(Document bsonDocument, Update update) {
        BuildUpdateFromDocument(bsonDocument, update, "");
    }

    // Recurse through A document turning each individual singleton field (not
    // arrays) into a
    // value in $set - i.e. { $set : { a:1, o.b: 2, o.c: 3 }}

    private void BuildUpdateFromDocument(Document bsonDocument, Update update, String basekey) {
        for (Map.Entry<String, Object> entry : bsonDocument.entrySet()) {
            // If it's a document then recurse
            // Dont recurese into Arrays (It's possible but there are catveates to think
            // about like deletions)
            if (entry.getValue() instanceof Document) {
                BuildUpdateFromDocument((Document) entry.getValue(), update, basekey + entry.getKey() + ".");
            } else {
                update.set(basekey + entry.getKey(), entry.getValue());
            }
        }
    }

    @Async("loadExecutor")
    public void asyncWriteMany(List<T> toSave) {
         asyncWriteMany(toSave, false);
    }

    @Async("loadExecutor")
    public void asyncWriteMany(List<T> toSave, boolean useUpdateNotReplace) {
        try {
            // Update some thread safe counts for upsertes, deletes and modifications.
            BulkWriteResult r = writeMany(toSave,useUpdateNotReplace);
            updates.addAndGet(r.getModifiedCount());
            deletes.addAndGet(r.getDeletedCount());
            inserts.addAndGet(r.getUpserts().size());

        } catch (Exception e) {
            logger.error(e.getMessage());
            // TODO Handle Failed writes going to a dead letter queue or similar.
            
        }
    }

    public void resetStats() {
        inserts.set(0);
        updates.set(0);
        deletes.set(0);
    }

    public AtomicInteger getUpdates() {
        return updates;
    }

    public AtomicInteger getDeletes() {
        return deletes;
    }

    public AtomicInteger getInserts() {
        return inserts;
    }

}
