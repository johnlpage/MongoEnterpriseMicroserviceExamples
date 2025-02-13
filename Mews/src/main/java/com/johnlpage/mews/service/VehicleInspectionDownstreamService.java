package com.johnlpage.mews.service;

import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.repository.VehicleInspectionRepository;
import java.util.stream.Stream;
import org.bson.json.JsonObject;
import org.springframework.stereotype.Service;

// This is intended for downstream service that want to get reported on or perhaps
// augmented data

@Service
public class VehicleInspectionDownstreamService {
  private final VehicleInspectionRepository vehicleInspectionRepository;

  public VehicleInspectionDownstreamService(
      VehicleInspectionRepository vehicleInspectionRepository) {
    this.vehicleInspectionRepository = vehicleInspectionRepository;
  }

  public Stream<JsonObject> nativeJsonExtractStream(String formatRequired) {
    return vehicleInspectionRepository.nativeJsonExtract(formatRequired, VehicleInspection.class);
  }

  public Stream<VehicleInspection> jsonExtractStream() {
    return vehicleInspectionRepository.findAllBy();
  }
}
