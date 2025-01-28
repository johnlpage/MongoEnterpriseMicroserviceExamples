package com.johnlpage.mews.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.repository.VehicleInspectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VehicleInspectionMongoDbJsonLoaderServiceImpl
    extends MongoDbJsonLoaderService<VehicleInspection> {

  @Autowired
  public VehicleInspectionMongoDbJsonLoaderServiceImpl(
      VehicleInspectionRepository repository, ObjectMapper objectMapper, JsonFactory jsonFactory) {
    super(repository, objectMapper, jsonFactory);
  }
}
