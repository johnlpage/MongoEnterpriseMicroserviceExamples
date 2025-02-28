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
  public static final Class<VehicleInspection> MODELCLASS = VehicleInspection.class;
  private static final Logger LOG = LoggerFactory.getLogger(VehicleInspectionQueryService.class);
  private final VehicleInspectionRepository repository;

  public VehicleInspectionQueryService(VehicleInspectionRepository repository) {
    this.repository = repository;
  }

  public List<VehicleInspection> mongoDbNativeQuery(String jsonString) {

    int cost = repository.costMongoDbNativeQuery(jsonString, MODELCLASS);
    LOG.info("Query cost is {}, running anyway. ", cost);

    // You could take various approaches here, allow some, deny some, send "bad" queries to
    // secondaries Perhaps even enforce additional limits of query clauses - COLLSCANs sorted
    // reverse by a date field and limited for example

    return repository.mongoDbNativeQuery(jsonString, MODELCLASS);
  }

  public List<VehicleInspection> atlasSearchQuery(String jsonString) {
    return repository.atlasSearchQuery(jsonString, MODELCLASS);
  }

  public Optional<VehicleInspection> getInspectionById(Long id) {
    return repository.findById(id);
  }

  public Slice<VehicleInspection> getInspectionByExample(
      VehicleInspection probe, int page, int size) {
    ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreNullValues();
    Example<VehicleInspection> example = Example.of(probe, matcher);
    return repository.findAll(example, PageRequest.of(page, size));
  }
}
