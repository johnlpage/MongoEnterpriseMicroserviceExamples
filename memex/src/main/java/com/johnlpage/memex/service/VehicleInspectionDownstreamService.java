package com.johnlpage.memex.service;

import com.johnlpage.memex.model.VehicleInspection;
import com.johnlpage.memex.repository.VehicleInspectionRepository;
import java.util.stream.Stream;
import org.bson.json.JsonObject;
import org.springframework.stereotype.Service;

// This is intended for downstream service that want to get reported on or perhaps
// augmented data

@Service
public class VehicleInspectionDownstreamService {
  private final VehicleInspectionRepository repository;

  public VehicleInspectionDownstreamService(VehicleInspectionRepository repository) {
    this.repository = repository;
  }

  public Stream<JsonObject> nativeJsonExtractStream(String formatRequired) {
    return repository.nativeJsonExtract(formatRequired, VehicleInspection.class);
  }

  public Stream<VehicleInspection> jsonExtractStream() {
    return repository.findAllBy();
  }
}
