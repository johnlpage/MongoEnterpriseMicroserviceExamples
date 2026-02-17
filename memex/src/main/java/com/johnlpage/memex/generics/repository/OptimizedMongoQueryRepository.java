package com.johnlpage.memex.generics.repository;

import java.util.List;

public interface OptimizedMongoQueryRepository<T> {

    List<T> mongoDbNativeQuery(String jsonString, Class<T> clazz);

    List<T> atlasSearchQuery(String jsonString, Class<T> clazz);

    int costMongoDbNativeQuery(String jsonString, Class<T> clazz);
}
