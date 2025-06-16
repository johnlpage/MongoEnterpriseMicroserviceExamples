package com.johnlpage.memex.service.generic;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.memex.util.UpdateStrategy;
import com.johnlpage.memex.repository.optimized.OptimizedMongoLoadRepository;
import com.mongodb.bulk.BulkWriteResult;
import jakarta.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public abstract class MongoDbJsonStreamingLoaderService<T> {

  private static final Logger LOG =
      LoggerFactory.getLogger(MongoDbJsonStreamingLoaderService.class);
  private static final int BATCH_SIZE = 200;
  private final OptimizedMongoLoadRepository<T> repository;
  private final ObjectMapper objectMapper;
  private final JsonFactory jsonFactory;

  /** Parses a JSON stream object by object, assumes it's not an Array. */
  @Nullable
  public JsonStreamingLoadResponse loadFromJsonStream(
      InputStream inputStream,
      Class<T> type,
      InvalidDataHandlerService<T> invalidDataHandlerService,
      UpdateStrategy updateStrategy,
      PreWriteTriggerService<T> pretrigger,
      PostWriteTriggerService<T> posttrigger) {

    AtomicInteger updates = new AtomicInteger(0);
    AtomicInteger deletes = new AtomicInteger(0);
    AtomicInteger inserts = new AtomicInteger(0);
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

          if (pretrigger != null) {
            // For a mutable model
            pretrigger.modifyMutableDataPreWrite(document);
            // for an immutable model
            // document = pretrigger.newImmutableDataPreWritedocument);
          }

          count++;

          toSave.add(document);
          if (toSave.size() >= BATCH_SIZE) {
            List<T> copyOfToSave = List.copyOf(toSave);
            toSave.clear();
            futures.add(
                repository
                    .asyncWriteMany(
                        copyOfToSave, type, invalidDataHandlerService, updateStrategy, posttrigger)
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
                    toSave, type, invalidDataHandlerService, updateStrategy, posttrigger)
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
    } catch (EOFException | ClientAbortException eofe) {
      LOG.error("Load Terminated as sender disconnected: {}", eofe.getMessage());
      return null;
    } catch (Exception e) {
      LOG.error("Error during data load process: {}", e.getMessage());
      return new JsonStreamingLoadResponse(
          updates.get(), deletes.get(), inserts.get(), false, e.getMessage());
    }
  }

  @Data
  @AllArgsConstructor
  public static class JsonStreamingLoadResponse {
    int updates;
    int deletes;
    int inserts;
    boolean success;
    String message;
  }
}
