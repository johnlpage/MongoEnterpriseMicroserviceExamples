package com.johnlpage.mews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.model.VehicleInspection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

// This is just Layering Glue, Business logic goes here not direct database access
// That goes via the Repository, you may choose to add logging here for example

@Service
public class VehicleInspectionHistoryService extends HistoryTriggerService<VehicleInspection> {

  public VehicleInspectionHistoryService(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
    super(mongoTemplate, objectMapper);
  }
}
