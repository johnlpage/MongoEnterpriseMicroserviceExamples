package com.johnlpage.memex.generics.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.memex.generics.repository.OptimizedMongoLoadRepository;
import com.johnlpage.memex.util.UpdateStrategy;
import com.mongodb.bulk.BulkWriteResult;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public abstract class MongoDbJsonStreamingLoaderService<T> {

    private static final Logger LOG =
            LoggerFactory.getLogger(MongoDbJsonStreamingLoaderService.class);
    private final OptimizedMongoLoadRepository<T> repository;
    private final ObjectMapper objectMapper;
    private final JsonFactory jsonFactory;
    @Value("${mongo.jsonloader.batch-size:200}")
    private int batchSize;

    /**
     * Parses a JSON stream object by object, assumes it's not an Array.
     */
    @Nullable
    public JsonStreamingLoadResponse loadFromJsonStream(
            InputStream inputStream,
            Class<T> type,
            InvalidDataHandlerService<T> invalidDataHandlerService,
            UpdateStrategy updateStrategy,
            PreWriteTriggerService<T> preTrigger,
            PostWriteTriggerService<T> postTrigger) throws DataLoadException {

        AtomicLong updates = new AtomicLong(0);
        AtomicLong deletes = new AtomicLong(0);
        AtomicLong inserts = new AtomicLong(0);
        List<T> toSave = new ArrayList<>();
        List<CompletableFuture<BulkWriteResult>> futures = new ArrayList<>();

        int count = 0;
        long startTime = System.currentTimeMillis();
        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
             JsonParser parser = jsonFactory.createParser(bufferedInputStream)) {
            // Iterate over tokens in the stream
            while (!parser.isClosed()) {
                // Check if the current token is the start of a new JSON object
                JsonToken token = parser.nextToken();
                if (token == JsonToken.START_OBJECT) {
                    // Move the parser to the end of the current object
                    JsonNode node = objectMapper.readTree(parser);

                    T document = objectMapper.treeToValue(node, type);

                    if (preTrigger != null) {
                        // For a mutable model
                        preTrigger.modifyMutableDataPreWrite(document);
                        // for an immutable model
                        // document = pretrigger.newImmutableDataPreWritedocument);
                    }

                    count++;

                    toSave.add(document);
                    if (toSave.size() >= batchSize) {
                        List<T> copyOfToSave = List.copyOf(toSave);
                        toSave.clear();
                        futures.add(
                                repository
                                        .asyncWriteMany(
                                                copyOfToSave, type, invalidDataHandlerService, updateStrategy, postTrigger)
                                        .thenApply(
                                                bulkWriteResult -> {
                                                    updates.addAndGet(bulkWriteResult.getModifiedCount());
                                                    deletes.addAndGet(bulkWriteResult.getDeletedCount());
                                                    inserts.addAndGet(
                                                            bulkWriteResult.getUpserts().size()
                                                                    + bulkWriteResult.getInsertedCount());
                                                    return bulkWriteResult;
                                                }));
                    }
                }
            }
            if (!toSave.isEmpty()) {
                futures.add(
                        repository
                                .asyncWriteMany(
                                        toSave, type, invalidDataHandlerService, updateStrategy, postTrigger)
                                .thenApply(
                                        bulkWriteResult -> {
                                            updates.addAndGet(bulkWriteResult.getModifiedCount());
                                            deletes.addAndGet(bulkWriteResult.getDeletedCount());
                                            inserts.addAndGet(
                                                    bulkWriteResult.getUpserts().size() + bulkWriteResult.getInsertedCount());
                                            return bulkWriteResult;
                                        }));
            }
            final long endTime = System.currentTimeMillis();

            CompletableFuture<Void> allFutures =
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            // Wait for all futures to complete
            allFutures.join();
            LOG.info("Processed {} docs. Time taken: {}ms.", count, endTime - startTime);
            LOG.info("Modified: {} Added: {} Removed: {}", updates, inserts, deletes);
            return new JsonStreamingLoadResponse(updates.get(), deletes.get(), inserts.get(), true, "");
        } catch (Exception e) {
            LOG.error("Error during data load process: {}", e.getMessage());
            throw new DataLoadException(
                    updates.get(),
                    deletes.get(),
                    inserts.get(),
                    "Error during data load process: " + e.getMessage(),
                    e );
        }
    }

    @Data
    @AllArgsConstructor
    public static class JsonStreamingLoadResponse {
        long updates;
        long deletes;
        long inserts;
        boolean success;
        String message;
    }
}
