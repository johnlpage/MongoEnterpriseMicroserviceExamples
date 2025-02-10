package com.johnlpage.mews.service;

import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.repository.VehicleInspectionRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

// This is just Layering Glue, Business logic goes here not direct database access
// That goes via the Repository, you may choose to add logging here for example

@Service
public class VehicleInspectionQueryService {
  private static final Logger LOG = LoggerFactory.getLogger(VehicleInspectionQueryService.class);
  private final VehicleInspectionRepository vehicleInspectionRepository;

  public VehicleInspectionQueryService(VehicleInspectionRepository vehicleInspectionRepository) {
    this.vehicleInspectionRepository = vehicleInspectionRepository;
  }

  public List<VehicleInspection> mongoDbNativeQuery(String jsonString) {

    int cost =
        vehicleInspectionRepository.costMongoDbNativeQuery(jsonString, VehicleInspection.class);
    LOG.info("Query cost is {}, running anyway. ", cost);

    // You could take various approaches here, allow some, deny some, send "bad" queries to
    // secondaries Perhaps even enforce additional limits of query clauses - COLLSCANs sorted
    // reverse by a date field and limited for example

    List<VehicleInspection> rval =
        vehicleInspectionRepository.mongoDbNativeQuery(jsonString, VehicleInspection.class);

    return rval;
  }

  public List<VehicleInspection> atlasSearchQuery(String jsonString) {
    return vehicleInspectionRepository.atlasSearchQuery(jsonString, VehicleInspection.class);
  }

  public Optional<VehicleInspection> getInspectionById(Long vehicleId) {
    return vehicleInspectionRepository.findById(vehicleId);
  }

  public Slice<VehicleInspection> getInspectionByExample(
      VehicleInspection probe, int page, int size) {
    ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreNullValues();
    Example<VehicleInspection> example = Example.of(probe, matcher);
    return vehicleInspectionRepository.findAll(example, PageRequest.of(page, size));
  }
}
