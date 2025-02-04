package com.johnlpage.mews.service;

import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

import com.johnlpage.mews.model.VehicleInspection;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.bson.Document;
import org.bson.codecs.JsonObjectCodec;
import org.bson.json.JsonMode;
import org.bson.json.JsonObject;
import org.bson.json.JsonWriterSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class VehicleInspectionDownstreamServiceImpl
    extends MongoDbDownstreamService<VehicleInspection> {

  private final MongoTemplate mongoTemplate;
  JsonWriterSettings jsonWriterSettings;
  @Autowired private MongoOperations mongoOperations;
  @Autowired private MongoClient mongoClient;

  @Value("${spring.data.mongodb.database}")
  private String databaseName;

  public VehicleInspectionDownstreamServiceImpl(MongoTemplate mongoTemplate) {
    super(mongoTemplate);
    this.mongoTemplate = mongoTemplate;
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

  // This returns the entire current data set - it's used here
  // to show how streaming from the DB works
  // On my test laptop this gets 12MB/s

  public Stream<VehicleInspection> findAllInspections() {
    Query filter = new Query(); // Matches everything
    return mongoTemplate.stream(filter, VehicleInspection.class);
  }

  public Stream<JsonObject> findAllFast() {
    Query filter = new Query(); // Matches everything
    String collectionName = mongoOperations.getCollectionName(VehicleInspection.class);
    String formatRequired =
        """
{
  "testid": "$_id",
  "testdate": 1,
  "testclass":1,
  "testtype": 1,
  "testresult": 1,
  "testmileage": 1,
  "postcode": 1,
  "fuel": 1,
  "capacity": 1,
  "firstusedate": 1,
  "faileditems":1,
  "vehicle": 1
}
""";

    MongoDatabase database = mongoClient.getDatabase(databaseName);
    MongoCollection<JsonObject> inspections =
        database
            .getCollection(collectionName, JsonObject.class)
            .withCodecRegistry(
                fromProviders(
                    fromCodecs(new JsonObjectCodec(jsonWriterSettings)),
                    database.getCodecRegistry()));

    MongoCursor<JsonObject> cursor =
        inspections.find().projection(Document.parse(formatRequired)).iterator();
    return StreamSupport.stream(((Iterable<JsonObject>) () -> cursor).spliterator(), false)
        .onClose(cursor::close);
  }
}
