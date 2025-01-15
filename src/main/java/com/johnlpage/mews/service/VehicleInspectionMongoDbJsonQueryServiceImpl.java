package com.johnlpage.mews.service;

import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.repository.VehicleInspectionRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class VehicleInspectionMongoDbJsonQueryServiceImpl
    extends MongoDBQueryService<VehicleInspection, Long> {

  public VehicleInspectionMongoDbJsonQueryServiceImpl(
      VehicleInspectionRepository repository, MongoTemplate mongoTemplate) {
    super(repository, mongoTemplate);
  }
}
