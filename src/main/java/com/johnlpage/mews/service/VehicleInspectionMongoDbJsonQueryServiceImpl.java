package com.johnlpage.mews.service;

import com.johnlpage.mews.models.VehicleInspection;
import com.johnlpage.mews.repository.VehicleInspectionRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class VehicleInspectionMongoDbJsonQueryServiceImpl
    extends MongoDbJsonQueryService<VehicleInspection, Long> {

  public VehicleInspectionMongoDbJsonQueryServiceImpl(
      VehicleInspectionRepository repository, MongoTemplate mongoTemplate) {
    super(repository, mongoTemplate);
  }
}
