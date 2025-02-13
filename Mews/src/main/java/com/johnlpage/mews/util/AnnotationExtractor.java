package com.johnlpage.mews.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.johnlpage.mews.model.DeleteFlag;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;

public class AnnotationExtractor {

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
        if (field.get(model) != null) return true;
        return false;
      }
    }

    // If no field is annotated with @Id, handle this scenario according to your needs
    throw new IllegalArgumentException(
        "No field annotated with @Id found in model class " + modelClass.getName());
  }

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
