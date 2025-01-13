package com.johnlpage.mews.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.dto.PageDTO;
import com.johnlpage.mews.models.VehicleInspection;
import com.johnlpage.mews.service.MongoDbJsonLoaderService;
import com.johnlpage.mews.service.MongoDbJsonQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vehicles")
public class VehicleInspectionController {

  private static final Logger LOG = LoggerFactory.getLogger(VehicleInspectionController.class);
  private final MongoDbJsonLoaderService<VehicleInspection, Long> inspectionLoaderService;
  private final MongoDbJsonQueryService<VehicleInspection, Long> inspectionQueryService;
  private final ObjectMapper objectMapper;

  @Autowired
  public VehicleInspectionController(
      MongoDbJsonLoaderService<VehicleInspection, Long> inspectionLoaderService,
      MongoDbJsonQueryService<VehicleInspection, Long> inspectionQueryService,
      ObjectMapper objectMapper) {
    this.inspectionLoaderService = inspectionLoaderService;
    this.inspectionQueryService = inspectionQueryService;
    this.objectMapper = objectMapper;
  }

  /**
   * This could be something that reads a file, or even from a Kafka Queue as long as it gets a
   * stream of JSON data - using an HTTP endpoint to demonstrate.
   */
  @PostMapping("/inspections")
  public void loadFromStream(
      HttpServletRequest request,
      @RequestParam(name = "futz", required = false, defaultValue = "false") boolean futz,
      @RequestParam(name = "useUpdate", required = false, defaultValue = "false")
          boolean useUpdate) {

    inspectionLoaderService.useUpdateNotReplace(useUpdate);
    LOG.info("Load Starts futz={}, useUpdate = {}", futz, useUpdate);
    try {
      inspectionLoaderService.loadFromJSONStream(
          request.getInputStream(), VehicleInspection.class, futz);
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
  public ResponseEntity<PageDTO<VehicleInspection>> getInspectionsByModel(
      @PathVariable String model,
      @RequestParam(name = "page", required = false, defaultValue = "0") int page,
      @RequestParam(name = "size", required = false, defaultValue = "10") int size) {
    VehicleInspection probe = new VehicleInspection();

    // This is where we are hard coding a query for this endpoint.
    probe.setModel(model);

    Slice<VehicleInspection> returnPage =
        inspectionQueryService.getModelByExample(probe, page, size);
    PageDTO<VehicleInspection> response = new PageDTO<>(returnPage);
    return ResponseEntity.ok(response);
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
      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      // Convert list to JSON string
      String jsonResult = objectMapper.writeValueAsString(result);
      return ResponseEntity.ok(jsonResult);
    } catch (JsonProcessingException e) {
      return ResponseEntity.status(500).body("Error converting vehicles to JSON");
    }
  }
}
