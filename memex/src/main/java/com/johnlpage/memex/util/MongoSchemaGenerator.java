package com.johnlpage.memex.util;

import org.bson.Document;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.Date;
import java.util.UUID;

// Boot 3.x Validation imports
import jakarta.validation.constraints.*;

public class MongoSchemaGenerator {

  public static Document generateSchema(Class<?> clazz) {
    Set<Class<?>> visited = new HashSet<>();
    Document schema = buildSchema(clazz, visited);
    return new Document("$jsonSchema", schema);
  }

  private static Document buildSchema(Class<?> clazz, Set<Class<?>> visited) {
    if (visited.contains(clazz)) {
      // Prevent infinite loops on circular references
      return new Document("bsonType", "object");
    }
    visited.add(clazz);

    List<String> required = new ArrayList<>();
    Map<String, Document> properties = new LinkedHashMap<>();

    for (java.lang.reflect.Field reflectionField : clazz.getDeclaredFields()) {
      // Skip the ones we are not storing in the DB
      if (reflectionField.isAnnotationPresent(
              org.springframework.data.annotation.ReadOnlyProperty.class)
          || reflectionField.isAnnotationPresent(
              org.springframework.data.annotation.Transient.class)
          || java.lang.reflect.Modifier.isTransient(reflectionField.getModifiers())) {
        continue; // skip field entirely
      }

      String fieldName = getMongoFieldName(reflectionField);
      Class<?> fieldType = reflectionField.getType();
      Document propSchema;

      if (isSimpleType(fieldType)) {
        propSchema = generateSimpleTypeSchema(reflectionField);
      } else if (Collection.class.isAssignableFrom(fieldType)) {
        propSchema = new Document("bsonType", "array");
        Class<?> itemType = getCollectionElementType(reflectionField);
        if (itemType != null) {
          Document itemsSchema =
              isSimpleType(itemType) ? simpleBsonType(itemType) : buildSchema(itemType, visited);
          propSchema.put("items", itemsSchema);
        }
      } else if (fieldType.isArray()) {
        propSchema = new Document("bsonType", "array");
        Class<?> itemType = fieldType.getComponentType();
        Document itemsSchema =
            isSimpleType(itemType) ? simpleBsonType(itemType) : buildSchema(itemType, visited);
        propSchema.put("items", itemsSchema);
      } else if (Map.class.isAssignableFrom(fieldType)
          || org.bson.Document.class.isAssignableFrom(fieldType)) {
        // Arbitrary object
        propSchema = new Document("bsonType", "object");
        // No properties defined → allowed extra keys
      } else {
        // Embedded POJO
        propSchema = buildSchema(fieldType, visited);
      }

      if (reflectionField.isAnnotationPresent(NotNull.class)) {
        required.add(fieldName);
      }
      applyValidationAnnotations(reflectionField, propSchema);

      properties.put(fieldName, propSchema);
    }
    // Used in code that detects what has changed and allows efficient retriueval
    properties.put("__previousValues", new Document("bsonType", "object"));

    Document jsonSchema =
        new Document("bsonType", "object").append("properties", new Document(properties));

    if (!properties.isEmpty()) {
      // Only restrict if there are defined properties
      jsonSchema.append("additionalProperties", false);
    }

    if (!required.isEmpty()) {
      jsonSchema.append("required", required);
    }

    return jsonSchema;
  }

  private static String getMongoFieldName(java.lang.reflect.Field reflectionField) {
    // Special handling for @Id
    if (reflectionField.isAnnotationPresent(org.springframework.data.annotation.Id.class)) {
      return "_id";
    }
    // @Field override
    org.springframework.data.mongodb.core.mapping.Field fieldAnnotation =
        reflectionField.getAnnotation(org.springframework.data.mongodb.core.mapping.Field.class);
    return fieldAnnotation != null ? fieldAnnotation.value() : reflectionField.getName();
  }

  private static boolean isSimpleType(Class<?> type) {
    return mapJavaToBson(type) != null;
  }

  private static Document generateSimpleTypeSchema(java.lang.reflect.Field field) {
    Document schema = simpleBsonType(field.getType());
    applyValidationAnnotations(field, schema);
    return schema;
  }

  private static Document simpleBsonType(Class<?> type) {
    return new Document("bsonType", mapJavaToBson(type));
  }

  private static String mapJavaToBson(Class<?> type) {
    if (type == String.class) return "string";
    if (type == Integer.class || type == int.class) return "int";
    if (type == Long.class || type == long.class) return "long";
    if (type == Double.class || type == double.class) return "double";
    if (type == Boolean.class || type == boolean.class) return "bool";
    if (Date.class.isAssignableFrom(type)) return "date";
    if (UUID.class.isAssignableFrom(type)) return "string";
    return null;
  }

  private static void applyValidationAnnotations(java.lang.reflect.Field field, Document schema) {
    if (field.isAnnotationPresent(Min.class)) {
      schema.put("minimum", field.getAnnotation(Min.class).value());
    }
    if (field.isAnnotationPresent(Max.class)) {
      schema.put("maximum", field.getAnnotation(Max.class).value());
    }
    if (field.isAnnotationPresent(Email.class)) {
      schema.put("pattern", "^.+@.+$");
    }
    if (field.isAnnotationPresent(Size.class)) {
      Size size = field.getAnnotation(Size.class);
      if (schema.getString("bsonType").equals("string")) {
        schema.put("minLength", size.min());
        if (size.max() < Integer.MAX_VALUE) {
          schema.put("maxLength", size.max());
        }
      }
      if (schema.getString("bsonType").equals("array")) {
        schema.put("minItems", size.min());
        if (size.max() < Integer.MAX_VALUE) {
          schema.put("maxItems", size.max());
        }
      }
    }
  }

  private static Class<?> getCollectionElementType(java.lang.reflect.Field field) {
    Type genericType = field.getGenericType();
    if (genericType instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) genericType;
      Type[] actualTypes = pt.getActualTypeArguments();
      if (actualTypes.length > 0 && actualTypes[0] instanceof Class) {
        return (Class<?>) actualTypes[0];
      }
    }
    return null;
  }
}
