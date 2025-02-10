package com.johnlpage.mews.repository.optimized;

import java.util.List;

public interface OptimizedMongoQueryRepository<T> {

  public List<T> mongoDbNativeQuery(String jsonString, Class<T> clazz);

  public List<T> atlasSearchQuery(String jsonString, Class<T> clazz);

  int costMongoDbNativeQuery(String jsonString, Class<T> clazz);

  // TODO - Add GetCost function to repo

}
