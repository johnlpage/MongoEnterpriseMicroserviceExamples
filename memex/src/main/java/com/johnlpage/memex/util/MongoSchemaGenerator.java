package com.johnlpage.memex.util;

import jakarta.validation.constraints.*;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

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

        // Check if this is an unsupported BSON type before processing
        if (isMongoDBType(clazz) && mapJavaToBson(clazz) == null) {
            throw new IllegalArgumentException(
                    "Cannot generate schema for unsupported BSON type: " + clazz.getName());
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
            if (fieldType == Object.class) {
                // Bare Object - no validation, allow any BSON type
                propSchema = new Document();
            } else if (isSimpleType(fieldType)) {
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
                    || Document.class.isAssignableFrom(fieldType)) {
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

        // @Field override - only use if value is explicitly set
        org.springframework.data.mongodb.core.mapping.Field fieldAnnotation =
                reflectionField.getAnnotation(org.springframework.data.mongodb.core.mapping.Field.class);

        if (fieldAnnotation != null && !fieldAnnotation.value().isEmpty()) {
            return fieldAnnotation.value();
        }

        // Also check the 'name' attribute (alternative to 'value')
        if (fieldAnnotation != null && !fieldAnnotation.name().isEmpty()) {
            return fieldAnnotation.name();
        }

        // Fall back to the Java field name
        return reflectionField.getName();
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
        // Primitives
        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class) return "int";
        if (type == Long.class || type == long.class) return "long";
        if (type == Double.class || type == double.class) return "double";
        if (type == Boolean.class || type == boolean.class) return "bool";
        if (Date.class.isAssignableFrom(type)) return "date";
        if (type == java.time.Instant.class)
            return "date"; // Works as logn as we have a typemapper
        if (UUID.class.isAssignableFrom(type)) return "string";

        // Native BSON types (preferred for performance)
        if (ObjectId.class.isAssignableFrom(type)) return "objectId";
        if (org.bson.types.Binary.class.isAssignableFrom(type))
            return "binData";
        if (org.bson.types.Decimal128.class.isAssignableFrom(type))
            return "decimal";
        if (org.bson.types.BSONTimestamp.class.isAssignableFrom(type))
            return "timestamp";

        // Java types with auto-conversion (convenience over performance)
        if (type == java.math.BigDecimal.class) return "decimal";

        // Explicitly unsupported
        if (type == java.math.BigInteger.class) {
            throw new IllegalArgumentException(
                    "BigInteger is not directly supported. Use Long for values up to 2^63-1, "
                            + "or store as String/Decimal128 for larger values.");
        }

        return null;
    }

    private static boolean isSimpleType(Class<?> type) {
        // First check if we have explicit mapping
        if (mapJavaToBson(type) != null) {
            return true;
        }

        // Not simple if we should block recursion
        if (shouldBlockRecursion(type)) {
            throw new IllegalArgumentException(createErrorMessage(type));
        }

        return false;
    }

    private static boolean shouldBlockRecursion(Class<?> type) {
        if (type.getPackage() == null) return false;

        String pkg = type.getPackage().getName();

        return pkg.startsWith("java.math")
                || pkg.startsWith("java.time")
                || pkg.startsWith("java.sql")
                || pkg.startsWith("java.awt")
                || pkg.startsWith("javax.swing")
                || pkg.startsWith("org.bson")
                || pkg.startsWith("com.mongodb");
    }

    private static String createErrorMessage(Class<?> type) {
        String pkg = type.getPackage().getName();

        if (pkg.startsWith("java.math")) {
            return "Unsupported type: "
                    + type.getName()
                    + ". Use Decimal128 for high-precision decimals or convert to String/Double.";
        }

        if (pkg.startsWith("java.time")) {
            return "Unsupported type: "
                    + type.getName()
                    + ". Convert to java.util.Date or store as ISO-8601 String or epoch Long.";
        }

        if (pkg.startsWith("java.sql")) {
            return "Unsupported type: "
                    + type.getName()
                    + ". Convert to java.util.Date or appropriate BSON type.";
        }

        if (pkg.startsWith("org.bson") || pkg.startsWith("com.mongodb")) {
            return "Unsupported BSON type: "
                    + type.getName()
                    + ". Add explicit mapping in mapJavaToBson().";
        }

        return "Type " + type.getName() + " cannot be used for schema generation.";
    }

    private static boolean isMongoDBType(Class<?> type) {
        if (type.getPackage() == null) return false;
        String packageName = type.getPackage().getName();
        return packageName.startsWith("org.bson") || packageName.startsWith("com.mongodb");
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
