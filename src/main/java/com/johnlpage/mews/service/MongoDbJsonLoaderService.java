package com.johnlpage.mews.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.models.MewsModel;
import com.johnlpage.mews.repository.OptimizedMongoLoadRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
public class MongoDbJsonLoaderService<R extends OptimizedMongoLoadRepository<M> & MongoRepository<M,?>, M extends MewsModel> {
    private static final Logger logger = LoggerFactory.getLogger(MongoDbJsonLoaderService.class);
    ArrayList<M> toSave = null;
    private boolean useUpdateNotReplace;
    @Autowired
    private R repository;
    
    // Parses a JSON stream object by object, assumes it's not an Array

    public void loadFromJSONStream(InputStream inputStream, Class<M> type, Boolean modifyForTesting) {
        // Create a JsonFactory and ObjectMapper
        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        M fuzzer = null;

        if (modifyForTesting) {
            try {
                fuzzer = type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                logger.error(e.getMessage());
                return;
            }
        }

        int count = 0;
        repository.resetStats();
        repository.

        long startTime = System.nanoTime();
        try (JsonParser parser = factory.createParser(inputStream)) {
            // Iterate over tokens in the stream
            while (!parser.isClosed()) {
                // Check if the current token is the start of a new JSON object
                JsonToken token = parser.nextToken();
                if (token == JsonToken.START_OBJECT) {
                    // Move the parser to the end of the current object
                    JsonNode node = mapper.readTree(parser);

                    //Map The JSON to a HashMap
                    @SuppressWarnings("unchecked") Map<String, Object> resultMap = mapper.convertValue(node, HashMap.class);

                    // If modifyForTesting is true then change some values in it.
                    if (fuzzer != null && modifyForTesting) {
                        fuzzer.modifyDataForTest(resultMap);
                    }
                    M document = mapper.convertValue(resultMap, type);

                    /* Optionally store the JSON as a String or the Whole Hashmap as an
                    alternative to using @JsonAnySetter
                    document.setPayload(resultMap);
                    */

                    count++;
                    loadItem(document,type);
                }
            }
            if (toSave != null && toSave.size() > 0) {
                 // Alternative Options
                // repository.writeMany(toSave);
                 // repository.saveAll(toSave);
              
                repository.asyncWriteMany(toSave, type, useUpdateNotReplace);
            }
            long endTime = System.nanoTime();
            logger.info("Processed " + count + " docs. Time taken: " + ((endTime - startTime) / 1000000L) + "ms.");
            logger.info("Modified: " + repository.getUpdates() + " Added:" + repository.getInserts() + " Removed: " + repository.getDeletes());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Load these into MongoDB as efficiently as we can, build up batches
    // Then load the bactch asyncronously.

    private void loadItem(M item, Class<M> type) {
        if (toSave == null) {
            toSave = new ArrayList<M>();
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
