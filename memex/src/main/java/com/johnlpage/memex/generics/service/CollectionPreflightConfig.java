package com.johnlpage.memex.generics.service;

import com.mongodb.client.model.IndexModel;
import org.bson.Document;

import java.util.List;

/**
 * Interface for defining collection-specific preflight requirements.
 * Each model/collection can implement this to define its schema and indexes.
 */
public interface CollectionPreflightConfig {

    /**
     * @return The name of the collection
     */
    String getCollectionName();

    /**
     * @return The class to use for server-side schema enforcement, or null if none
     */
    Class<?> getSchemaClass();

    /**
     * @return List of index definitions as Documents, or empty list if none
     */
    default List<IndexModel> getIndexes() {
        return List.of();
    }

    /**
     * @return List of search index definitions, or empty list if none
     */
    default List<Document> getSearchIndexes() {
        return List.of();
    }

    /**
     * @return true if this collection should have an associated _history collection
     */
    default boolean hasHistoryCollection() {
        return true;
    }

    /**
     * The shard key field(s) for this collection, as they are stored in the document
     * (dot-notation for nested fields, e.g. "tenantId" or "location.state"). Do not
     * include "_id" itself.
     *
     * <p>When populated, {@code OptimizedMongoLoadRepositoryImpl} will add these fields
     * (when present on the item being written) to the query used for updates/replaces/
     * deletes, alongside "_id". This lets a mongos route the operation directly to the
     * correct shard instead of broadcasting to all shards.
     *
     * @return List of shard key field names, or empty list if this collection is unsharded
     *         or its shard key does not need to be targeted explicitly.
     */
    default List<String> getShardKeyFields() {
        return List.of();
    }
}
