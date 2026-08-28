package com.johnlpage.memex.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

public final class AnnotationExtractor {

  private AnnotationExtractor() {}

  public static String getCollectionName(Class<?> clazz) {
    if (clazz.isAnnotationPresent(Document.class)) {
      Document document = clazz.getAnnotation(Document.class);
      return document.collection();
    }
    // If no collection is specified, default to the class name in lowercase
    return clazz.getSimpleName().toLowerCase();
  }

  /**
   * Retrieves the value of the field annotated with @Id from the given model object.
   *
   * @param model the object from which to extract the _id field
   * @return the value of the _id field as an Object, or null if no field is annotated with @Id
   * @throws IllegalAccessException if the field is not accessible
   */
  public static Object getIdFromModel(Object model) throws IllegalAccessException {
    if (model == null) {
      throw new IllegalArgumentException("The provided model is null");
    }

    Class<?> modelClass = model.getClass();

    // Iterate through all declared fields in the class
    for (Field field : modelClass.getDeclaredFields()) {
      // Check if the field has the @Id annotation
      if (field.isAnnotationPresent(Id.class)) {
        // Make the field accessible if it's private or protected
        field.setAccessible(true);

        // Return the value of the field for the given model instance
        return field.get(model);
      }
    }

    // If no field is annotated with @Id, handle this scenario according to your needs
    throw new IllegalArgumentException(
        "No field annotated with @Id found in model class " + modelClass.getName());
  }

  /**
   * Returns the Java field name of the {@code @Id}-annotated field on a class, if any.
   *
   * <p>This is class-based (rather than instance-based like {@link #getIdFromModel}) since it's
   * used for static validation of configuration - e.g. checking that a shard key field name
   * doesn't accidentally reference the {@code @Id} field's Java name instead of the literal
   * document key {@code "_id"} that Spring Data MongoDB always maps {@code @Id} fields to.
   *
   * @param clazz the model class to inspect
   * @return the Java field name of the {@code @Id} field, or null if none is found
   */
  @Nullable
  public static String getIdFieldName(Class<?> clazz) {
    if (clazz == null) {
      return null;
    }
    for (Field field : clazz.getDeclaredFields()) {
      if (field.isAnnotationPresent(Id.class)) {
        return field.getName();
      }
    }
    return null;
  }

  public static Boolean hasDeleteFlag(Object model) throws IllegalAccessException {
    if (model == null) {
      throw new IllegalArgumentException("The provided model is null");
    }

    Class<?> modelClass = model.getClass();

    // Iterate through all declared fields in the class
    for (Field field : modelClass.getDeclaredFields()) {
      // Check if the field has the @Id annotation
      if (field.isAnnotationPresent(DeleteFlag.class)) {
        // Make the field accessible if it's private or protected
        field.setAccessible(true);

        // Return the value of the field for the given model instance
        return field.get(model) != null;
      }
    }

    // If no field is annotated with @Id, handle this scenario according to your needs
    throw new IllegalArgumentException(
        "No field annotated with @Id found in model class " + modelClass.getName());
  }

  public static Field getVersionField(Object model) throws IllegalAccessException {
    if (model == null) {
      throw new IllegalArgumentException("The provided model is null");
    }

    Class<?> modelClass = model.getClass();

    // Iterate through all declared fields in the class
    for (Field field : modelClass.getDeclaredFields()) {
      // Check if the field has the @Id annotation
      if (field.isAnnotationPresent(Version.class)) {
        field.setAccessible(true);
        // return a writeable field Object
        return field;
      }
    }

    return null;
  }

  /**
   * Resolves the value of a field on a model instance by its MongoDB document field name,
   * supporting dot-notation for nested (embedded document) fields, e.g. "location.state".
   *
   * <p>This is used to pull shard key values out of a partially-populated model (an item may
   * only have the _id field and a subset of other fields set) so they can be added to a query.
   * If any segment of the path is missing, not accessible, or null, this returns {@code null}
   * rather than throwing, since a partial item may legitimately not have the shard key
   * populated.
   *
   * @param item the object (or nested object) to read the field from
   * @param dbFieldPath the document field path, dot-separated for nested fields
   * @return the resolved value, or null if it could not be found/resolved
   */
  @Nullable
  public static Object getNestedFieldValue(Object item, String dbFieldPath) {
    if (item == null || dbFieldPath == null || dbFieldPath.isEmpty()) {
      return null;
    }

    Object current = item;
    for (String segment : dbFieldPath.split("\\.")) {
      if (current == null) {
        return null;
      }
      Field field = findFieldByDbName(current.getClass(), segment);
      if (field == null) {
        return null;
      }
      field.setAccessible(true);
      try {
        current = field.get(current);
      } catch (IllegalAccessException e) {
        return null;
      }
    }
    return current;
  }

  /**
   * Finds a declared field on a class matching a MongoDB document field name - first by
   * checking for an explicit {@code @Field} annotation value, then falling back to matching
   * the Java field name directly.
   */
  @Nullable
  private static Field findFieldByDbName(Class<?> clazz, String dbFieldName) {
    for (Field field : clazz.getDeclaredFields()) {
      org.springframework.data.mongodb.core.mapping.Field mongoField =
          field.getAnnotation(org.springframework.data.mongodb.core.mapping.Field.class);
      if (mongoField != null && dbFieldName.equals(mongoField.value())) {
        return field;
      }
    }
    try {
      return clazz.getDeclaredField(dbFieldName);
    } catch (NoSuchFieldException e) {
      return null;
    }
  }

  @Nullable
  public static String getDatabaseFieldNameByJsonProperty(Class<?> clazz, String jsonPropertyName) {
    for (Field field : clazz.getDeclaredFields()) {
      JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
      org.springframework.data.mongodb.core.mapping.Field mongoField =
          field.getAnnotation(org.springframework.data.mongodb.core.mapping.Field.class);
      if (jsonProperty != null
          && jsonPropertyName.equals(jsonProperty.value())
          && mongoField != null) {
        return mongoField.value();
      }
    }
    return null; // or throw an exception if not found
  }

  public static Map<String, Object> renameKeysRecursively(Class<?> clazz, Map<String, Object> map) {
    Map<String, Object> updatedMap = new HashMap<>();
    for (Map.Entry<String, Object> entry : map.entrySet()) {

      String newKey = getDatabaseFieldNameByJsonProperty(clazz, entry.getKey());
      newKey = newKey == null ? entry.getKey() : newKey;
      Object value = entry.getValue();
      if (value instanceof Map) {
        value = renameKeysRecursively(clazz, (Map<String, Object>) value);
      }
      updatedMap.put(newKey, value);
    }
    return updatedMap;
  }
}
