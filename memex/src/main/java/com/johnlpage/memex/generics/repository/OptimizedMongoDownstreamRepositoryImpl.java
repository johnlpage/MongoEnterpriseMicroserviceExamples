package com.johnlpage.memex.generics.repository;

import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.bson.Document;
import org.bson.codecs.JsonObjectCodec;
import org.bson.json.JsonMode;
import org.bson.json.JsonObject;
import org.bson.json.JsonWriterSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoOperations;

/**
 * This class has generic methods to do common reporting and extraction tasks
 */
public class OptimizedMongoDownstreamRepositoryImpl<T>
        implements OptimizedMongoDownstreamRepository<T> {

    private final MongoOperations mongoOperations;
    private final MongoClient mongoClient;

    JsonWriterSettings jsonWriterSettings;

    @Value("${spring.data.mongodb.database}")
    private String databaseName;

    public OptimizedMongoDownstreamRepositoryImpl(
            MongoOperations mongoOperations, MongoClient mongoClient) {
        this.mongoOperations = mongoOperations;
        this.mongoClient = mongoClient;

        this.jsonWriterSettings =
                JsonWriterSettings.builder()
                        .outputMode(JsonMode.RELAXED)
                        .objectIdConverter((value, writer) -> writer.writeString(value.toHexString()))
                        .decimal128Converter((value, writer) -> writer.writeNumber(value.toString()))
                        .dateTimeConverter(
                                (value, writer) -> {
                                    ZonedDateTime zonedDateTime = Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC);
                                    writer.writeString(DateTimeFormatter.ISO_DATE_TIME.format(zonedDateTime));
                                })
                        .build();
    }

    private static <T> Stream<T> stream(MongoCursor<T> cursor) {
        int characteristics = Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.ORDERED;
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(cursor, characteristics);
        return StreamSupport.stream(spliterator, false).onClose(cursor::close);
    }

    /**
     * This returns the entire current data set - it's used here to show how streaming from the DB
     * works. On my test laptop this gets 12MB/s.
     */
    public Stream<JsonObject> nativeJsonExtract(String formatRequired, Class<T> modelClazz) {

        String collectionName = mongoOperations.getCollectionName(modelClazz);
        // TODO - generate the projection from the Model automatically so this is generic rather than
        // requiring a format. Not sure if we should do or not.

        MongoDatabase database = mongoClient.getDatabase(databaseName);
        MongoCollection<JsonObject> jsonDocs =
                database
                        .getCollection(collectionName, org.bson.json.JsonObject.class)
                        .withCodecRegistry(
                                fromProviders(
                                        fromCodecs(new JsonObjectCodec(jsonWriterSettings)),
                                        database.getCodecRegistry()));

        MongoCursor<JsonObject> cursor =
                jsonDocs.find().projection(Document.parse(formatRequired)).iterator();
        return stream(cursor);
    }
}
