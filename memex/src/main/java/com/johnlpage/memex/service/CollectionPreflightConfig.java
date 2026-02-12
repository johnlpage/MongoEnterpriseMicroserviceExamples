package com.johnlpage.memex.service;

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
}
