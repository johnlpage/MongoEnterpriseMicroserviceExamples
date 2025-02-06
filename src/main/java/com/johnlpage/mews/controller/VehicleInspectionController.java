package com.johnlpage.mews.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.dto.PageDto;
import com.johnlpage.mews.model.UpdateStrategy;
import com.johnlpage.mews.model.Vehicle;
import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.bson.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Slice;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RequiredArgsConstructor
@RestController
@RequestMapping("/vehicles")
public class VehicleInspectionController {

  private static final Logger LOG = LoggerFactory.getLogger(VehicleInspectionController.class);
  private final VehicleInspectionMongoDbJsonLoaderServiceImpl inspectionLoaderService;
  private final VehicleInspectionMongoQueryServiceImpl inspectionQueryService;
  private final VehicleInspectionPreWriteTriggerServiceImpl inspectionPreWriteTriggerService;
  private final VehicleInspectionPostWriteTriggerServiceImpl inspectionPostWriteTriggerService;
  private final VehicleInspectionDownstreamServiceImpl downstreamService;

  private final ObjectMapper objectMapper;

  /**
   * This could be something that reads a file, or even from a Kafka Queue as long as it gets a
   * stream of JSON data - using an HTTP endpoint to demonstrate.
   */
  @PostMapping("/inspections")
  public void loadFromStream(
      HttpServletRequest request,
      @RequestParam(name = "futz", required = false, defaultValue = "false") Boolean futz,
      @RequestParam(name = "updateStrategy", required = false, defaultValue = "REPLACE")
          UpdateStrategy updateStrategy) {
    try {

      inspectionLoaderService.loadFromJsonStream(
          request.getInputStream(),
          VehicleInspection.class,
          updateStrategy,
          futz ? inspectionPreWriteTriggerService : null,
          updateStrategy.equals(UpdateStrategy.UPDATEWITHHISTORY)
              ? inspectionPostWriteTriggerService
              : null);

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
    v.setModel(model);
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

  @PostMapping("/inspections/search")
  public ResponseEntity<String> atlasSearch(@RequestBody String requestBody) {
    List<VehicleInspection> result =
        inspectionQueryService.getModelByAtlasSearch(requestBody, VehicleInspection.class);
    try {
      // Convert list to JSON string
      String jsonResult = objectMapper.writeValueAsString(result);
      return ResponseEntity.ok(jsonResult);
    } catch (JsonProcessingException e) {
      return ResponseEntity.status(500).body("Error converting vehicles to JSON");
    }
  }

  // If we want to stream back data we can do it like this (or Flux and Reactive Mongo)
  // This does all the mapping in the client from Document->VI->JSON - we can do this faster
  // By skipping the object creation and doing it all in a projection on the server.
  // Then using RAWBsonDocument

  @GetMapping(value = "/inspections/stream", produces = MediaType.APPLICATION_JSON_VALUE)
  public StreamingResponseBody streamInspections() {
    return outputStream -> {
      try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
          Stream<VehicleInspection> inspectionStream = downstreamService.findAllInspections()) {
        boolean isFirst = true;
        for (VehicleInspection inspection :
            (Iterable<VehicleInspection>) inspectionStream::iterator) {
          if (!isFirst) {
            bufferedOutputStream.write("\n".getBytes());
          }
          bufferedOutputStream.write(objectMapper.writeValueAsBytes(inspection));
          bufferedOutputStream.flush(); // Ensure data is sent promptly
          isFirst = false;
        }
      } catch (IOException e) {
        LOG.error("Error during streaming inspections: {}", e.getMessage(), e);
      }
    };
  }

  //  Native version of streamInspections using RAWBson to populate JSONObject
  // Have to tell MongoDB how to do the mapping
  // TODO - generate the mapping from the Model automatically

  @GetMapping(value = "/inspections/streamfast", produces = MediaType.APPLICATION_JSON_VALUE)
  public StreamingResponseBody streamInspectionsFast() {
    return outputStream -> {
      try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
          Stream<JsonObject> inspectionStream = downstreamService.findAllFast()) {
        boolean isFirst = true;
        for (JsonObject inspection : (Iterable<JsonObject>) inspectionStream::iterator) {
          if (!isFirst) {
            bufferedOutputStream.write("\n".getBytes());
          }
          bufferedOutputStream.write(inspection.getJson().getBytes());
          bufferedOutputStream.flush(); // Ensure data is sent promptly
          isFirst = false;
        }
      } catch (IOException e) {
        LOG.error("Error during streaming inspections: {}", e.getMessage(), e);
      }
    };
  }
}
