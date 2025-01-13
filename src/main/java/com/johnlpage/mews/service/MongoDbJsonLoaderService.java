package com.johnlpage.mews.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.models.MewsModel;
import com.johnlpage.mews.repository.GenericOptimizedMongoLoadRepository;
import java.io.EOFException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MongoDbJsonLoaderService<T extends MewsModel<ID>, ID> {

  private static final Logger LOG = LoggerFactory.getLogger(MongoDbJsonLoaderService.class);
  private final GenericOptimizedMongoLoadRepository<T, ID> repository;
  private final ObjectMapper objectMapper;
  private final JsonFactory jsonFactory;

  ArrayList<T> toSave = null;
  private boolean useUpdateNotReplace;

  @Autowired
  public MongoDbJsonLoaderService(
      GenericOptimizedMongoLoadRepository<T, ID> repository,
      ObjectMapper objectMapper,
      JsonFactory jsonFactory) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.jsonFactory = jsonFactory;
  }

  /** Parses a JSON stream object by object, assumes it's not an Array. */
  public void loadFromJSONStream(InputStream inputStream, Class<T> type, Boolean modifyForTesting) {
    // Create a JsonFactory and ObjectMapper
    T fuzzer = null;

    if (modifyForTesting) {
      try {
        fuzzer = type.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        LOG.error(e.getMessage());
        return;
      }
    }

    int count = 0;
    repository.resetStats();

    long startTime = System.nanoTime();
    try (JsonParser parser = jsonFactory.createParser(inputStream)) {
      // Iterate over tokens in the stream
      while (!parser.isClosed()) {
        // Check if the current token is the start of a new JSON object
        JsonToken token = parser.nextToken();
        if (token == JsonToken.START_OBJECT) {
          // Move the parser to the end of the current object
          JsonNode node = objectMapper.readTree(parser);

          // Map The JSON to a HashMap
          @SuppressWarnings("unchecked")
          Map<String, Object> resultMap = objectMapper.convertValue(node, HashMap.class);

          // If modifyForTesting is true then change some values in it.
          if (fuzzer != null && modifyForTesting) {
            fuzzer.modifyDataForTest(resultMap);
          }
          T document = objectMapper.convertValue(resultMap, type);

          /* Optionally store the JSON as a String or the Whole Hashmap as an
          alternative to using @JsonAnySetter
          document.setPayload(resultMap);
          */

          count++;
          loadItem(document, type);
        }
      }
      if (toSave != null && !toSave.isEmpty()) {
        // Alternative Options
        // repository.writeMany(toSave);
        // repository.saveAll(toSave);

        repository.asyncWriteMany(toSave, type, useUpdateNotReplace);
      }
      long endTime = System.nanoTime();
      LOG.info("Processed {} docs. Time taken: {}ms.", count, (endTime - startTime) / 1000000L);
      LOG.info(
          "Modified: {} Added:{} Removed: {}",
          repository.getUpdates(),
          repository.getInserts(),
          repository.getDeletes());

    } catch (EOFException e) {
      LOG.error("Load Terminated as sender stopped sending JSON: {}", e.getMessage(), e);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    }
  }

  // Load these into MongoDB as efficiently as we can, build up batches
  // Then load the batch asynchronously.

  private void loadItem(T item, Class<T> type) {
    if (toSave == null) {
      toSave = new ArrayList<>();
    }
    toSave.add(item);
    if (toSave.size() >= 100) {
      // Alternative Options
      // repository.writeMany(toSave);
      // repository.saveAll(toSave);

      repository.asyncWriteMany(toSave, type, useUpdateNotReplace);
      toSave = null; // Dont clear existing
    }
  }

  public void useUpdateNotReplace(boolean b) {
    useUpdateNotReplace = b;
  }
}
