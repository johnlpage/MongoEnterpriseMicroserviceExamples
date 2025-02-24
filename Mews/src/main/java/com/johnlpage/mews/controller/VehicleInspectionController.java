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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.bson.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api")
public class VehicleInspectionController {

  private static final Logger LOG = LoggerFactory.getLogger(VehicleInspectionController.class);
  private final VehicleInspectionQueryService queryService;
  private final VehicleInspectionJsonLoaderService loaderService;
  private final VehicleInspectionPreWriteTriggerService preWriteTriggerService;
  private final VehicleInspectionHistoryService postWriteTriggerService;
  private final VehicleInspectionDownstreamService downstreamService;

  private final ObjectMapper objectMapper;

  /**
   * This could be something that reads a file, or even from a Kafka Queue as long as it gets a
   * stream of JSON data - using an HTTP endpoint to demonstrate.
   */
  @PostMapping("/inspections")
  public ResponseEntity<MongoDbJsonStreamingLoaderService.JsonStreamingLoadResponse> loadFromStream(
      HttpServletRequest request,
      @RequestParam(name = "futz", required = false, defaultValue = "false") Boolean futz,
      @RequestParam(name = "updateStrategy", required = false, defaultValue = "REPLACE")
          UpdateStrategy updateStrategy) {
    LOG.info("Load inspections from JSON stream starting...");
    MongoDbJsonStreamingLoaderService.JsonStreamingLoadResponse rval;
    try {
      rval =
          loaderService.loadFromJsonStream(
              request.getInputStream(),
              VehicleInspection.class,
              updateStrategy,
              futz ? preWriteTriggerService : null,
              updateStrategy.equals(UpdateStrategy.UPDATEWITHHISTORY)
                  ? postWriteTriggerService
                  : null);

      return new ResponseEntity<>(rval, HttpStatus.OK);
    } catch (Exception e) {
      rval =
          new MongoDbJsonStreamingLoaderService.JsonStreamingLoadResponse(
              0, 0, 0, false, e.getMessage());

      // Log the exception if necessary and return HTTP 500 Internal Server Error
      return new ResponseEntity<>(rval, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /** Get By ID - */
  @GetMapping("/inspections/id/{id}")
  public ResponseEntity<VehicleInspection> getInspectionById(@PathVariable Long id) {
    // While you can to straight to the repository it's best practise to use a service.
    return queryService
        .getInspectionById(id)
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
    // By creating an example of the class, clumsy but works.
    VehicleInspection example = new VehicleInspection();
    Vehicle v = new Vehicle();
    v.setModel(model);
    example.setVehicle(v);

    // Use the line below for immutable model
    // VehicleInspection probe = VehicleInspection.builder().model(model).build();
    Slice<VehicleInspection> returnPage = queryService.getInspectionByExample(example, page, size);
    PageDto<VehicleInspection> entity = new PageDto<>(returnPage);
    return ResponseEntity.ok(entity);
  }

  /**
   * This is a very "Raw" API interface that lets the caller design their own query and projection
   * etc.
   */
  @PostMapping("/inspections/query")
  public ResponseEntity<String> mongoQuery(@RequestBody String requestBody) {
    List<VehicleInspection> result = queryService.mongoDbNativeQuery(requestBody);
    try {
      // Convert list to JSON string
      String jsonResult = objectMapper.writeValueAsString(result);
      return ResponseEntity.ok(jsonResult);
    } catch (JsonProcessingException e) {
      return ResponseEntity.status(500).body("Error converting vehicles to JSON");
    }
  }

  @PostMapping("/inspections/search")
  public ResponseEntity<String> atlasSearchQuery(@RequestBody String requestBody) {
    List<VehicleInspection> result = queryService.atlasSearchQuery(requestBody);
    try {
      // Convert list to JSON string
      String jsonResult = objectMapper.writeValueAsString(result);
      return ResponseEntity.ok(jsonResult);
    } catch (JsonProcessingException e) {
      return ResponseEntity.status(500).body("Error converting vehicles to JSON");
    }
  }

  /**
   * If we want to stream back data we can do it like this (or Flux and Reactive Mongo) This does
   * all the mapping in the client from Document->VI->JSON - we can do this faster By skipping the
   * object creation and doing it all in a projection on the server. Then using RAWBsonDocument
   */
  @GetMapping(value = "/inspections/json", produces = MediaType.APPLICATION_JSON_VALUE)
  public StreamingResponseBody streamInspections() {
    return outputStream -> {
      try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
          Stream<VehicleInspection> inspectionStream = downstreamService.jsonExtractStream()) {
        boolean isFirst = true;
        Iterator<VehicleInspection> it = inspectionStream.iterator();
        while (it.hasNext()) {
          VehicleInspection inspection = it.next();
          if (!isFirst) {
            bufferedOutputStream.write("\n".getBytes());
          }
          bufferedOutputStream.write(objectMapper.writeValueAsBytes(inspection));
          isFirst = false;
        }
      } catch (IOException e) {
        LOG.error("Error during streaming inspections using Spring mode: {}", e.getMessage());
      }
    };
  }

  /**
   * Native version of streamInspections using RAWBson to populate JSONObject Have to tell MongoDB
   * how to do the mapping
   */
  @GetMapping(value = "/inspections/jsonnative", produces = MediaType.APPLICATION_JSON_VALUE)
  public StreamingResponseBody streamInspectionsFast() {

    String formatRequired =
        """
        {
          "testid": "$_id",
          "testdate": 1,
          "testclass":1,
          "testtype": 1,
          "testresult": 1,
          "testmileage": 1,
          "postcode": 1,
          "fuel": 1,
          "capacity": 1,
          "firstusedate": 1,
          "faileditems":1,
          "vehicle": 1
        }
        """;

    return outputStream -> {
      try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
          Stream<JsonObject> inspectionStream =
              downstreamService.nativeJsonExtractStream(formatRequired)) {
        boolean isFirst = true;
        Iterator<JsonObject> iterator = inspectionStream.iterator();
        while (iterator.hasNext()) {
          JsonObject inspection = iterator.next();
          if (!isFirst) {
            bufferedOutputStream.write("\n".getBytes());
          }
          bufferedOutputStream.write(inspection.getJson().getBytes());
          isFirst = false;
        }
      } catch (IOException e) {
        LOG.error("Error during streaming inspections using native mode: {}", e.getMessage());
      }
    };
  }
}
