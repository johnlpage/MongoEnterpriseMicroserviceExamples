package com.johnlpage.memex.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;

/** Maps model/JSON field paths to the field paths stored in MongoDB. */
public final class MongoFieldPathMapper {

    private MongoFieldPathMapper() {}

    public static String mapFieldPath(
            String fieldPath,
            MongoPersistentEntity<?> rootEntity,
            Function<Class<?>, MongoPersistentEntity<?>> entityLookup) {
        if (fieldPath == null || fieldPath.isEmpty()) {
            return fieldPath;
        }

        String[] segments = fieldPath.split("\\.", -1);
        List<String> mappedSegments = new ArrayList<>(segments.length);
        MongoPersistentEntity<?> entity = rootEntity;

        for (String segment : segments) {
            if (entity == null) {
                mappedSegments.add(segment);
                continue;
            }

            MongoPersistentProperty property = findProperty(entity, segment);
            if (property == null) {
                mappedSegments.add(segment);
                entity = null;
                continue;
            }

            mappedSegments.add(property.getFieldName());
            entity = property.isEntity() ? entityLookup.apply(property.getActualType()) : null;
        }

        return String.join(".", mappedSegments);
    }

    public static Document mapAtlasSearchSpec(
            Document searchSpec,
            MongoPersistentEntity<?> rootEntity,
            Function<Class<?>, MongoPersistentEntity<?>> entityLookup) {
        return (Document) mapSearchValue(searchSpec, rootEntity, entityLookup);
    }

    public static Document mapProjection(
            Document projection,
            MongoPersistentEntity<?> rootEntity,
            Function<Class<?>, MongoPersistentEntity<?>> entityLookup) {
        Document mappedProjection = new Document();
        for (Map.Entry<String, Object> entry : projection.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("$")) {
                key = mapFieldPath(key, rootEntity, entityLookup);
            }
            mappedProjection.put(key, entry.getValue());
        }
        return mappedProjection;
    }

    private static Object mapSearchValue(
            Object value,
            MongoPersistentEntity<?> rootEntity,
            Function<Class<?>, MongoPersistentEntity<?>> entityLookup) {
        if (value instanceof Document document) {
            Document mapped = new Document();
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                Object mappedValue = entry.getValue();
                if ("path".equals(entry.getKey()) && mappedValue instanceof String path) {
                    mappedValue = mapFieldPath(path, rootEntity, entityLookup);
                } else if ("paths".equals(entry.getKey()) && mappedValue instanceof List<?> paths) {
                    mappedValue = paths.stream()
                            .map(path -> path instanceof String stringPath
                                    ? mapFieldPath(stringPath, rootEntity, entityLookup)
                                    : path)
                            .toList();
                } else {
                    mappedValue = mapSearchValue(mappedValue, rootEntity, entityLookup);
                }
                mapped.put(entry.getKey(), mappedValue);
            }
            return mapped;
        }

        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> mapSearchValue(item, rootEntity, entityLookup))
                    .toList();
        }

        return value;
    }

    private static MongoPersistentProperty findProperty(
            MongoPersistentEntity<?> entity, String propertyName) {
        MongoPersistentProperty property = entity.getPersistentProperty(propertyName);
        if (property != null) {
            return property;
        }

        for (MongoPersistentProperty candidate : entity) {
            JsonProperty jsonProperty = candidate.findPropertyOrOwnerAnnotation(JsonProperty.class);
            if (jsonProperty != null
                    && !jsonProperty.value().isEmpty()
                    && jsonProperty.value().equals(propertyName)) {
                return candidate;
            }
        }
        return null;
    }
}
