package com.johnlpage.mews.repository.optimized;

import java.util.stream.Stream;
import org.bson.json.JsonObject;

/*
 * This class has generic methods to do common reporting and extraction tasks
 *
 */

public interface OptimizedMongoDownstreamRepository<T> {

  Stream<JsonObject> nativeJsonExtract(String formatRequired, Class<T> modelClazz);
}
