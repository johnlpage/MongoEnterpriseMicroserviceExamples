package com.johnlpage.mews.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.johnlpage.mews.models.MEWSModel;

import com.johnlpage.mews.repository.OptimizedMongoLoadRepository;

@Service
public class MongoDBJSONLoaderService<R extends OptimizedMongoLoadRepository<M>, M extends MEWSModel> {
    private static final Logger logger = LoggerFactory.getLogger(MongoDBJSONLoaderService.class);
    private boolean useUpdateNotReplace;

    @Autowired
    private R repository;

    ArrayList<M> toSave = null;

    // Parses a JSON stream object by object, assumes it's not an Array

    public void loadFromJSONStream(InputStream inputStream, Class<M> type, Boolean modifyForTesting) {
        // Create a JsonFactory and ObjectMapper
        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        M fuzzer= null;

        if (modifyForTesting) {
                try {
                    fuzzer = type.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    logger.info("Here");
                  logger.error(e.getMessage());
                  return;
                } 
        }

        int count = 0;
        repository.resetStats();

        long startTime = System.nanoTime();
        try (JsonParser parser = factory.createParser(inputStream);) {
            // Iterate over tokens in the stream
            while (!parser.isClosed()) {
                // Check if the current token is the start of a new JSON object
                JsonToken token = parser.nextToken();
                if (token == JsonToken.START_OBJECT) {
                    // Move the parser to the end of the current object
                    JsonNode node = mapper.readTree(parser);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = mapper.convertValue(node, HashMap.class);

                    // If modifyForTesting is true then change some values in it.
                    if (fuzzer != null && modifyForTesting) {
                        fuzzer.modifyDataForTest(resultMap);
                    }

                    // TODO: Optionally remove things from payload that are explicitly mapped
                    // perhaps programatically. Payload could also be a (optionally compressed)
                    // string.

                    Document payload = new Document(resultMap);
                    M document = (M) mapper.convertValue(resultMap, type);
                    document.setPayload(payload);

                    count++;
                    loadItem(document);
                }
            }
            if (toSave != null && toSave.size() > 0) {
                // Sync Version = repository.writeMany(toSave);
                repository.asyncWriteMany(toSave, useUpdateNotReplace);
            }
            long endTime = System.nanoTime();
            logger.info("Processed " + count + " docs. Time taken: " + ((endTime - startTime) / 1000000L) + "ms.");
            logger.info("Modified: " + repository.getUpdates() + " Added:" + repository.getInserts()
                    + " Removed: " + repository.getInserts());

            ;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Load these into MongoDB as efficiently as we can, build up batches
    // Then load the bactch asyncronously.

    private void loadItem(M item) {
        if (toSave == null) {
            toSave = new ArrayList<M>();
        }
        toSave.add(item);
        if (toSave.size() >= 100) {
            // Sync Version = repository.writeMany(toSave);
            repository.asyncWriteMany(toSave, useUpdateNotReplace);
            toSave = null; // Dont clear existing
        }
    }

    public void useUpdateNotReplace(boolean b) {
        useUpdateNotReplace = b;
    }

}
