package com.johnlpage.mews.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.repository.optimized.OptimizedMongoLoadRepository;
import org.springframework.stereotype.Service;

// This is just Layering Glue, Business logic goes here not direct database access
// That goes via the Repository, you may choose to add logging here for example

@Service
public class VehicleInspectionJsonLoaderService
    extends MongoDbJsonStreamingLoaderService<VehicleInspection> {

  public VehicleInspectionJsonLoaderService(
      OptimizedMongoLoadRepository<VehicleInspection> repository,
      ObjectMapper objectMapper,
      JsonFactory jsonFactory) {
    super(repository, objectMapper, jsonFactory);
  }
}
