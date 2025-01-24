package com.johnlpage.mews.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.dto.PageDto;
import com.johnlpage.mews.model.UpdateStrategy;
import com.johnlpage.mews.model.Vehicle;
import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.service.VehicleInspectionPostWriteTriggerServiceImpl;
import com.johnlpage.mews.service.VehicleInspectionPreWriteTriggerServiceImpl;
import com.johnlpage.mews.service.VehicleInspectionMongoDbJsonLoaderServiceImpl;
import com.johnlpage.mews.service.VehicleInspectionMongoQueryServiceImpl;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/vehicles")
public class VehicleInspectionController {

  private static final Logger LOG = LoggerFactory.getLogger(VehicleInspectionController.class);
  private final VehicleInspectionMongoDbJsonLoaderServiceImpl inspectionLoaderService;
  private final VehicleInspectionMongoQueryServiceImpl inspectionQueryService;
  private final VehicleInspectionPreWriteTriggerServiceImpl inspectionPreWriteTriggerService;
  private final VehicleInspectionPostWriteTriggerServiceImpl inspectionPostWriteTriggerService;

  private final ObjectMapper objectMapper;

  /**
   * This could be something that reads a file, or even from a Kafka Queue as long as it gets a
   * stream of JSON data - using an HTTP endpoint to demonstrate.
   */
  @PostMapping("/inspections")
  public void loadFromStream(
      HttpServletRequest request,
      @RequestParam(name = "futz", required = false, defaultValue = "false") Boolean futz,
      @RequestParam(name = "updateStrategy", required = false, defaultValue = "REPLACE") UpdateStrategy updateStrategy) {

    LOG.info("Load Starts futz={}, updateStrategy = {}", futz, updateStrategy);
    try {
      inspectionLoaderService.loadFromJsonStream(
          request.getInputStream(), VehicleInspection.class, updateStrategy, futz ? inspectionPreWriteTriggerService : null, inspectionPostWriteTriggerService);
    } catch (Exception e) {
      LOG.error(e.getMessage());
    }
  }




  /** Get By ID */
  @GetMapping("/inspections/{id}")
  public ResponseEntity<VehicleInspection> getInspectionById(@PathVariable Long id) {

    return inspectionQueryService
        .getModelById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * JPA Get By Example Query - Needs an Index to be efficient It still finds ALL the results each
   * time and returns a subset
   */
  @GetMapping("/inspections/model/{model}")
  public ResponseEntity<PageDto<VehicleInspection>> getInspectionsByModel(
      @PathVariable String model,
      @RequestParam(name = "page", required = false, defaultValue = "0") int page,
      @RequestParam(name = "size", required = false, defaultValue = "10") int size) {

    // This is where we are hard coding a query for this endpoint.
    // use setModel in a mutable model

    VehicleInspection probe = new VehicleInspection();
    Vehicle v = new Vehicle();
    v.setVehicleModel(model);
    probe.setVehicle(v);

    // Use the line below for immutable model
    // VehicleInspection probe = VehicleInspection.builder().model(model).build();

    Slice<VehicleInspection> returnPage =
        inspectionQueryService.getModelByExample(probe, page, size);
    PageDto<VehicleInspection> entity = new PageDto<>(returnPage);
    return ResponseEntity.ok(entity);
  }

  /**
   * This is a very "Raw" API interface that lets the caller design their own query and projection
   * etc.
   */
  @PostMapping("/inspections/query")
  public ResponseEntity<String> mongoQuery(@RequestBody String requestBody) {
    LOG.info(requestBody);
    List<VehicleInspection> result =
        inspectionQueryService.getModelByMongoQuery(requestBody, VehicleInspection.class);
    try {
      // Convert list to JSON string
      String jsonResult = objectMapper.writeValueAsString(result);
      return ResponseEntity.ok(jsonResult);
    } catch (JsonProcessingException e) {
      return ResponseEntity.status(500).body("Error converting vehicles to JSON");
    }
  }
}
