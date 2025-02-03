package com.johnlpage.mews.service;

import com.johnlpage.mews.model.VehicleInspection;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class VehicleInspectionDownstreamServiceImpl
    extends MongoDbDownstreamService<VehicleInspection> {

  private final MongoTemplate mongoTemplate;

  public VehicleInspectionDownstreamServiceImpl(MongoTemplate mongoTemplate) {
    super(mongoTemplate);
    this.mongoTemplate = mongoTemplate;
  }

  // This returns the entire current data set - it's used here
  // to show how streaming from the DB works

  public Stream<VehicleInspection> findAllInspections() {
    Query filter = new Query(); // Matches everything
    return mongoTemplate.stream(filter, VehicleInspection.class);
  }
}
