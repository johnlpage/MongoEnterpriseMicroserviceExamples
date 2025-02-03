package com.johnlpage.mews.service;

import java.time.LocalDate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

// This is intended for downstream service that want to get reported on or perhaps
// augmented data
@Service
@RequiredArgsConstructor
public class MongoDbDownstreamService<T> {
  private final MongoTemplate mongoTemplate;

  // This fetches raw data from the DB which you then need to map to the class you want, or to
  // return as  JSON if that's the aim.

  Stream<Document> getChangeHistory(
      Object identifier, Class<T> clazz, LocalDate fromDate, LocalDate toDate) {
    // Get the base colleciton
    // Define the $match stage
    AggregationOperation match = Aggregation.match(Criteria.where("status").is("SHIPPED"));
    // Define the $lookup stage
    AggregationOperation lookup =
        Aggregation.lookup("customers", "customerId", "_id", "customerDetails");
    // Create the aggregation pipeline
    Aggregation pipeline = Aggregation.newAggregation(match, lookup);

    return mongoTemplate.aggregateStream(pipeline, clazz, Document.class);
  }
}
