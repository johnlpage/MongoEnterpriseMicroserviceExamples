package com.johnlpage.memex.service;

import com.johnlpage.memex.config.MongoVersionBean;
import com.johnlpage.memex.model.DocumentHistory;
import com.johnlpage.memex.util.MongoSchemaGenerator;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.BsonDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Preflight check service that discovers and processes all CollectionPreflightConfig beans.
 * Automatically creates _history collections for configs that enable history tracking.
 */
@Service
public class MongoDbPreflightCheckService {
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbPreflightCheckService.class);

    private static final String HISTORY_SUFFIX = "_history";

    private final ApplicationContext context;
    private final MongoTemplate mongoTemplate;
    private final List<CollectionPreflightConfig> collectionConfigs;

    @Value("${memex.preflight.createRequiredIndexes:true}")
    private boolean createRequiredIndexes;

    @Value("${memex.preflight.createCollections:true}")
    private boolean createCollections;

    @Value("${memex.preflight.createSearchIndexes:true}")
    private boolean createSearchIndexes;

    @Value("${memex.preflight.enforceSchema:true}")
    private boolean enforceSchema;

    @Value("${memex.mongodb.hasSearch:true}")
    private boolean hasSearch;

    @Value("${memex.preflight.schemaOnCreate:true}")
    private boolean schemaOnCreate;

    @Value("${memex.preflight.dropAllCollections:false}")
    private boolean dropAllCollections;

    public MongoDbPreflightCheckService(
            ApplicationContext context,
            MongoTemplate mongoTemplate,
            List<CollectionPreflightConfig> collectionConfigs) {
        this.context = context;
        this.mongoTemplate = mongoTemplate;
        this.collectionConfigs = collectionConfigs;
    }

    /**
     * Build the complete list of collection requirements including auto-generated history collections.
     */
    private List<CollectionRequirement> buildCollectionRequirements() {
        List<CollectionRequirement> requirements = new ArrayList<>();

        for (CollectionPreflightConfig config : collectionConfigs) {
            // Add the main collection
            requirements.add(new CollectionRequirement(
                    config.getCollectionName(),
                    config.getSchemaClass(),
                    config.getIndexes(),
                    config.getSearchIndexes()
            ));

            // Add history collection if enabled
            if (config.hasHistoryCollection()) {
                String historyCollectionName = config.getCollectionName() + HISTORY_SUFFIX;
                List<IndexModel> historyIndexes = List.of(

                        new IndexModel(Indexes.ascending("recordId", "timestamp"))
                );

                requirements.add(new CollectionRequirement(
                        historyCollectionName,
                        DocumentHistory.class,
                        historyIndexes,
                        List.of()
                ));
            }
        }

        return requirements;
    }

    /**
     * Ensure all Collections exist, create them or quit depending on flags.
     */
    void ensureCollectionsExist(List<CollectionRequirement> requirements) {
        MongoDatabase database = mongoTemplate.getDb();
        List<String> existingCollections = database.listCollectionNames().into(new ArrayList<>());

        for (CollectionRequirement requirement : requirements) {
            String collectionName = requirement.collectionName();
            Class<?> schemaClass = requirement.schemaClass();
            Document validator = null;

            if (schemaClass != null) {
                try {
                    validator = MongoSchemaGenerator.generateSchema(schemaClass);
                    LOG.info("Schema for {} = '{}' ", collectionName, validator.toJson());
                } catch (Exception e) {
                    LOG.error("Could not process class {}: {}", schemaClass.getName(), e.getMessage());
                }
            }

            // Rarely used but useful if you have no easy way to drop the DB
            if (dropAllCollections && existingCollections.contains(collectionName)) {
                LOG.warn(
                        "WARNING: FORCE DROPPING COLLECTION {} !!! - disable in app config if you don't want to",
                        collectionName);
                mongoTemplate.dropCollection(collectionName);
                existingCollections.remove(collectionName);
            }

            if (!existingCollections.contains(collectionName)) {
                if (createCollections) {
                    if (schemaOnCreate && validator != null) {
                        ValidationOptions validationOptions =
                                new ValidationOptions()
                                        .validator(validator)
                                        .validationLevel(ValidationLevel.MODERATE)
                                        .validationAction(ValidationAction.ERROR);
                        CreateCollectionOptions options =
                                new CreateCollectionOptions().validationOptions(validationOptions);
                        database.createCollection(collectionName, options);
                        LOG.warn(
                                "Collection '{}' does not exist, creating it with schema validation.",
                                collectionName);
                    } else {
                        LOG.warn("Collection '{}' does not exist, creating it.", collectionName);
                        database.createCollection(collectionName);
                    }
                } else {
                    LOG.error("Collection '{}' does not exist, cancelling startup", collectionName);
                    int exitCode = SpringApplication.exit(context, () -> 0);
                    System.exit(exitCode);
                }
            }

            if (validator != null && enforceSchema && !schemaOnCreate) {
                try {
                    Document collModCmd =
                            new Document("collMod", collectionName)
                                    .append("validator", validator)
                                    .append("validationLevel", "moderate")
                                    .append("validationAction", "error");

                    database.runCommand(collModCmd);
                    LOG.info("Enforcing Schema Validation with collMod based on {}",
                            schemaClass.getName());
                } catch (Exception e) {
                    LOG.error(
                            "Error enforcing schema validation for class {}: {}",
                            schemaClass.getName(), e.getMessage());
                }
            }
        }
    }

    /**
     * Ensure all required indexes exist
     */
    void ensureRequiredIndexesExist(List<CollectionRequirement> requirements) {
        for (CollectionRequirement requirement : requirements) {
            String collectionName = requirement.collectionName();
            List<IndexModel> requiredIndexes = requirement.indexes();

            if (requiredIndexes.isEmpty()) {
                continue;
            }
            // This does not yet support Index OPTION like unique - only keys
            MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);
            List<String> existingIndexes =
                    collection
                            .listIndexes()
                            .map(index -> index.get("key", Document.class).toJson())
                            .into(new ArrayList<>());

            for (IndexModel index : requiredIndexes) {
                BsonDocument indexKeys = index.getKeys().toBsonDocument();
                if (existingIndexes.contains(indexKeys.toJson())) {
                    continue;
                }
                if (createRequiredIndexes) {
                    LOG.warn("Index '{}' does not exist on '{}', creating required index",
                            indexKeys.toJson(), collectionName);
                    collection.createIndex(indexKeys);
                } else {
                    LOG.error(
                            "Collection '{}' does not have index {}, cancelling startup",
                            collectionName,
                            indexKeys.toJson());
                    SpringApplication.exit(context, () -> 0);
                    return;
                }
            }
        }
    }

    void ensureRequiredSearchIndexesExist(List<CollectionRequirement> requirements) {
        if (!hasSearch) {
            return;
        }

        for (CollectionRequirement requirement : requirements) {
            String collectionName = requirement.collectionName();
            List<Document> requiredSearchIndexes = requirement.searchIndexes();

            if (requiredSearchIndexes.isEmpty()) {
                continue;
            }

            // Get existing search indexes
            AggregationResults<Document> results;
            AggregationOperation listIndexes = Aggregation.stage("{\"$listSearchIndexes\" : {}}");
            try {
                results = mongoTemplate.aggregate(
                        Aggregation.newAggregation(listIndexes), collectionName, Document.class);
            } catch (Exception e) {
                LOG.error(
                        "ERROR: memex.mongodb.hasSearch=true but Search not available: {}",
                        e.getMessage());
                return;
            }

            Map<String, Document> existingSearchIndexMap = new HashMap<>();
            for (Document existingSearchIndex : results) {
                String key = existingSearchIndex.getString("name");
                existingSearchIndexMap.put(key, existingSearchIndex);
            }

            // Check required indexes
            for (Document requiredSearchIndex : requiredSearchIndexes) {
                String requiredName = requiredSearchIndex.getString("name");
                Document requiredDefinition = requiredSearchIndex.get("definition", Document.class);
                Document searchIndexInfo = existingSearchIndexMap.get(requiredName);

                if (searchIndexInfo != null) {
                    String latestDefinition =
                            searchIndexInfo.get("latestDefinition", Document.class).toJson();

                    if (!latestDefinition.equals(requiredDefinition.toJson())) {
                        LOG.error(
                                "Definition of index '{}'  \n{}  is not \n{}",
                                requiredName,
                                latestDefinition,
                                requiredDefinition);
                    }

                    if (!searchIndexInfo.getString("status").equals("READY")) {
                        LOG.warn(
                                "--->>> Search index '{}' is still not yet ready: status {}",
                                requiredName,
                                searchIndexInfo.getString("status"));
                    }
                } else {
                    LOG.warn("Collection '{}' does not have searchIndex {}",
                            collectionName, requiredName);
                    if (createSearchIndexes) {
                        LOG.info("Creating Search Index");
                        createSearchIndex(collectionName, requiredName, requiredDefinition);
                    } else {
                        LOG.error("Exiting due to missing index {}", requiredName);
                        SpringApplication.exit(context, () -> 0);
                        return;
                    }
                }
            }
        }
    }

    @Bean
    public ApplicationRunner mongoPreflightCheck(MongoVersionBean mongoVersionBean) {
        return args -> {
            LOG.info("*** PREFLIGHT CHECK STARTED ***");
            LOG.info(
                    "MongoDB Version: {}.{}",
                    mongoVersionBean.getMajorVersion(),
                    mongoVersionBean.getMinorversion());
            LOG.info("Discovered {} collection configurations", collectionConfigs.size());

            for (CollectionPreflightConfig config : collectionConfigs) {
                LOG.info("  - {} (schema: {}, history: {})",
                        config.getCollectionName(),
                        config.getSchemaClass() != null ? config.getSchemaClass().getSimpleName() : "none",
                        config.hasHistoryCollection());
            }

            if (createRequiredIndexes) {
                LOG.warn(
                        "WARNING: MEMEX IS CONFIGURED TO AUTOMATICALLY CREATE MISSING INDEXES - "
                                + "THIS IS NOT RECOMMENDED IN PRODUCTION!");
            }

            LOG.info("createRequiredIndexes: {}", createRequiredIndexes);
            LOG.info("createCollections: {}", createCollections);
            LOG.info("createSearchIndexes: {}", createSearchIndexes);
            LOG.info("enforceSchema: {}", enforceSchema);
            LOG.info("hasSearch: {}", hasSearch);
            LOG.info("schemaOnCreate: {}", schemaOnCreate);

            // Build complete list of requirements including history collections
            List<CollectionRequirement> requirements = buildCollectionRequirements();
            LOG.info("Total collections to check (including history): {}", requirements.size());

            ensureCollectionsExist(requirements);
            ensureRequiredIndexesExist(requirements);
            ensureRequiredSearchIndexesExist(requirements);

            LOG.info("*** PREFLIGHT CHECK COMPLETE ***");
        };
    }

    /**
     * Create an Atlas Search Index from a String definition
     */
    void createSearchIndex(String collection, String name, Document definition) {
        Document createSearchIndexCommand =
                new Document("createSearchIndexes", collection)
                        .append(
                                "indexes",
                                Collections.singletonList(
                                        new Document("name", name)
                                                .append("type", "search")
                                                .append("definition", definition)));

        MongoDatabase database = mongoTemplate.getDb();
        database.runCommand(createSearchIndexCommand);
    }

    /**
     * Internal record to hold collection requirements (both configured and auto-generated).
     */
    private record CollectionRequirement(
            String collectionName,
            Class<?> schemaClass,
            List<com.mongodb.client.model.IndexModel> indexes,
            List<Document> searchIndexes
    ) {
    }
}
