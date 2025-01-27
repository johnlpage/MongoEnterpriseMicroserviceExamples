package com.johnlpage.mews.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

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
            "indexes": [ { "vehicle.model" : 1 }]
          },
          { "name" : "inspectionhistory" }
        ]
    }
    """;



  private final ApplicationContext context;
  private final MongoTemplate mongoTemplate;
  private final boolean createRequiredIndexes = true;
  private final boolean createCollections = true;

  public MongoPreflightConfig(ApplicationContext context, MongoTemplate mongoTemplate) {
    this.context = context;
    this.mongoTemplate = mongoTemplate;
  }

  @Bean
  public ApplicationRunner mongoPreflightCheck(MongoTemplate mongoTemplate) {
    return args -> {
      MongoDatabase database = mongoTemplate.getDb();

      LOG.info("PREFLIGHT CHECK");
      if(createRequiredIndexes) {
        LOG.warn("THIS IS CONFIGURED TO AUTOMATICALLY CREATE INDEXES - THIS IS NOT RECOMMENDED IN PRODUCTION");
      }

      Document scheamAndIndexes = Document.parse(SCHEMA_AND_INDEXES);

      List<String> existingCollections = new ArrayList<String>();
      database.listCollectionNames().into(existingCollections);
      LOG.info(existingCollections.toString());
      for (Document requiredCollection : scheamAndIndexes.getList("collections", Document.class)) {
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
            existingIndexes.add(existingIndex.toJson());
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
      }
      LOG.info("PREFLIGHT CHECK COMPLETE");
    };

  }
}
