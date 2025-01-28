package com.johnlpage.mews.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.util.*;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;

// In this case we check we have the collections and indexes we require

@Configuration
public class MongoPreflightConfig {
  private static final Logger LOG = LoggerFactory.getLogger(MongoPreflightConfig.class);

  // Defining this a JSON makes it easy to read but still hard coded.
  // todo - create Atlas Search indexes
  // todo - create Atlas Vector Indexes
  // todo - extend this to include schema validation check.
  private static final String SCHEMA_AND_INDEXES =
      """
    {
        "collections" : [
          { "name" : "inspections" ,
            "indexes": [ { "vehicle.model" : 1 }],
            "searchIndexes" : [ { "name": "default", "definition" : { "mappings" : { "dynamic" : true, fields: {}} }}]
          },
          { "name" : "inspectionhistory" }
        ]
    }
    """;

  private final ApplicationContext context;
  private final MongoTemplate mongoTemplate;
  private final boolean createRequiredIndexes = true;
  private final boolean createCollections = true;
  private final boolean createSearchIndexes = true;

  public MongoPreflightConfig(ApplicationContext context, MongoTemplate mongoTemplate) {
    this.context = context;
    this.mongoTemplate = mongoTemplate;
  }

  // Define the createSearchIndex command
  Document createSearchIndex(String collection, String name, String JsonDefinition) {
    Document createSearchIndexCommand =
        new Document("createSearchIndexes", collection)
            .append(
                "indexes",
                Arrays.asList(
                    new Document("name", name)
                        .append("type", "search")
                        .append("definition", Document.parse(JsonDefinition))));

    // Run the command
    MongoDatabase database = mongoTemplate.getDb();
    return database.runCommand(createSearchIndexCommand);
  }

  @Bean
  public ApplicationRunner mongoPreflightCheck(MongoTemplate mongoTemplate) {
    return args -> {
      MongoDatabase database = mongoTemplate.getDb();

      LOG.info("PREFLIGHT CHECK");
      if (createRequiredIndexes) {
        LOG.warn(
            "THIS IS CONFIGURED TO AUTOMATICALLY CREATE INDEXES - THIS IS NOT RECOMMENDED IN PRODUCTION");
      }

      Document schemaAndIndexes = Document.parse(SCHEMA_AND_INDEXES);

      List<String> existingCollections = new ArrayList<String>();
      database.listCollectionNames().into(existingCollections);
      LOG.info(existingCollections.toString());
      for (Document requiredCollection : schemaAndIndexes.getList("collections", Document.class)) {
        String collectionName = requiredCollection.getString("name");
        if (!existingCollections.contains(collectionName)) {
          if (createCollections) {
            LOG.error("Collection '{}' does not exist, creating it.", collectionName);
            database.createCollection(collectionName);
          } else {
            LOG.error("Collection '{}' does not exist, cancelling startup", collectionName);
            int exitCode = SpringApplication.exit(context, () -> 0);
            System.exit(exitCode);
          }
        }

        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);

        List<Document> requiredIndexes = requiredCollection.getList("indexes", Document.class);
        List<String> existingIndexes = new ArrayList<String>();
        if (requiredIndexes != null) {
          for (Document existingIndex : collection.listIndexes()) {
            existingIndexes.add(existingIndex.get("key",Document.class).toJson());
            LOG.info(existingIndex.toJson());
          }

          for (Document index : requiredIndexes) {
            if (!existingIndexes.contains(index.toJson())) {
              if (createRequiredIndexes) {
                LOG.warn("Index '{}' does not exist, creating required index", index.toJson());
                collection.createIndex(index);
              } else {
                LOG.error(
                    "Collection '{}' does not have index {}, cancelling startup",
                    collectionName,
                    index.toJson());
                int exitCode = SpringApplication.exit(context, () -> 0);
                return;
              }
            }
          }
        }

        List<Document> requiredSearchIndexes =
            requiredCollection.getList("searchIndexes", Document.class);

        if (requiredSearchIndexes != null) {

          // Get a list of the indexes that are defined and their statuses

          AggregationOperation listIndexes = Aggregation.stage("{\"$listSearchIndexes\" : {}}");
          AggregationResults<Document> results =
              mongoTemplate.aggregate(
                  Aggregation.newAggregation(listIndexes), collectionName, Document.class);
          Map<String, Document> resultsearchIndexMap = new HashMap<String, Document>();
          for (Document existingSearchIndex : results) {
            LOG.info(existingSearchIndex.toJson());
            String key = existingSearchIndex.getString("name");
            resultsearchIndexMap.put(key, existingSearchIndex);
          }

          // Itterate over the indexes we requires

          for (Document requiredSearchIndex : requiredSearchIndexes) {
            String requiredName = requiredSearchIndex.getString("name");
            String requiredDefinition = requiredSearchIndex.get("definition", Document.class).toJson();
            Document searchIndexInfo= resultsearchIndexMap.get(requiredName);
            boolean failed = false;
            if (searchIndexInfo != null) {

              String latestDefinition = searchIndexInfo.get("latestDefinition", Document.class).toJson();

              if (latestDefinition.equals(requiredDefinition)
                  == false) {
                LOG.error(
                    "Definition of index '{}'  \n{}  is not \n{}",
                    requiredName,
                    latestDefinition,
                    requiredDefinition);
                failed = true;
              }

              if (searchIndexInfo.getString("status").equals("READY") == false) {
                LOG.warn("--->>> Search index '{}' is still not yet ready", requiredName);

              }
            } else {
              LOG.error(
                  "Collection '{}' does not have searchIndex {}",
                  collectionName,
                  requiredName);
              failed = true;
            }

            if (failed) {
              if (createSearchIndexes) {
                // TODO make search index
                LOG.info("Creating Search Index");
                createSearchIndex(collectionName, requiredName, requiredDefinition);
              } else {
                int exitCode = SpringApplication.exit(context, () -> 0);
                return;
              }
            }
          }
        }
      }
      LOG.info("PREFLIGHT CHECK COMPLETE");
    };
  }
}
