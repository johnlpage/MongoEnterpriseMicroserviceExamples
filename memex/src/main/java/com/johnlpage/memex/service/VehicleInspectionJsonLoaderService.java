package com.johnlpage.memex.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.memex.model.VehicleInspection;
import com.johnlpage.memex.repository.optimized.OptimizedMongoLoadRepository;
import com.johnlpage.memex.service.generic.MongoDbJsonStreamingLoaderService;
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
