package com.johnlpage.memex.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.memex.dto.PageDto;
import com.johnlpage.memex.model.UpdateStrategy;
import com.johnlpage.memex.model.Vehicle;
import com.johnlpage.memex.model.VehicleInspection;
import com.johnlpage.memex.repository.VehicleInspectionRepository;
import com.johnlpage.memex.service.*;
import com.johnlpage.memex.service.generic.MongoDbJsonStreamingLoaderService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.bson.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Slice;
import org.springframework.format.annotation.DateTimeFormat;
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
  private final VehicleInspectionHistoryTriggerService postWriteTriggerService;
  private final VehicleInspectionDownstreamService downstreamService;
  private final VehicleInspectionHistoryService historyService;
  private final VehicleInspectionInvalidDataHandlerService invalidDataHandlerService;

  private final ObjectMapper objectMapper;
  private final VehicleInspectionRepository vehicleInspectionRepository;

  @PostMapping("/inspection")
  public void oneinspection(@RequestBody VehicleInspection inspection) {
    LOG.warn("Saving vehicle inspection: {}", inspection);
    vehicleInspectionRepository.save(inspection);
  }

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
    LOG.info("Load data from JSON stream starting...");
    MongoDbJsonStreamingLoaderService.JsonStreamingLoadResponse returnValue;
    try {
      returnValue =
          loaderService.loadFromJsonStream(
              request.getInputStream(),
              VehicleInspection.class,
              invalidDataHandlerService,
              updateStrategy,
              futz ? preWriteTriggerService : null,
              updateStrategy.equals(UpdateStrategy.UPDATEWITHHISTORY)
                  ? postWriteTriggerService
                  : null);

      return new ResponseEntity<>(returnValue, HttpStatus.OK);
    } catch (Exception e) {
      returnValue =
          new MongoDbJsonStreamingLoaderService.JsonStreamingLoadResponse(
              0, 0, 0, false, e.getMessage());

      // Log the exception if necessary and return HTTP 500 Internal Server Error
      return new ResponseEntity<>(returnValue, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /** Get By ID - */
  @GetMapping("/inspections/id/{id}")
  public ResponseEntity<VehicleInspection> getById(@PathVariable Long id) {
    // While you can to straight to the repository it's best practise to use a service.
    return queryService
        .getById(id)
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
    Slice<VehicleInspection> returnPage = queryService.getByExample(example, page, size);
    PageDto<VehicleInspection> entity = new PageDto<>(returnPage);
    return ResponseEntity.ok(entity);
  }

  /**
   * This is a very "Raw" API interface that lets the caller design their own query and projection
   * etc.
   */
  @PostMapping(value ="/inspections/query", produces = MediaType.APPLICATION_JSON_VALUE)
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
  public ResponseEntity<StreamingResponseBody> streamJson() {

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(outputStream ->
                    writeDocumentsToOutputStream(outputStream, downstreamService.jsonExtractStream()));
  }

  /**
   * Native version of streamJson using RAWBson to populate JSONObject, this uses abotu half the CPU
   * Have to tell MongoDB how to do any mapping of fields in the format though.
   */
  @GetMapping(value = "/inspections/jsonnative", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<StreamingResponseBody> streamJsonNative() {

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

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(outputStream -> {
              try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
                   Stream<JsonObject> stream = downstreamService.nativeJsonExtractStream(formatRequired)) {
                boolean isFirst = true;
                Iterator<JsonObject> iterator = stream.iterator();
                while (iterator.hasNext()) {
                  JsonObject jsonObject = iterator.next();
                  if (!isFirst) {
                    bufferedOutputStream.write("\n".getBytes());
                  }
                  bufferedOutputStream.write(jsonObject.getJson().getBytes());
                  isFirst = false;
                }
              } catch (IOException e) {
                LOG.error("Error during streaming jsonObjects using native mode: {}", e.getMessage());
              }
            });
  }

  @GetMapping(value = "/inspections/asOf", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<StreamingResponseBody> dataAtDate(
          @RequestParam(name = "asOfDate") @DateTimeFormat(pattern = "yyyyMMddHHmmss") Date asOfDate,
          @RequestParam(name = "id") Long id) {
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(outputStream ->
                    writeDocumentsToOutputStream(outputStream, historyService.asOfDate(id, asOfDate)));
  }

  private void writeDocumentsToOutputStream(
      OutputStream outputStream, Stream<VehicleInspection> recordStream) {
    try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream)) {
      boolean isFirst = true;
      for (VehicleInspection record : (Iterable<VehicleInspection>) recordStream::iterator) {
        if (!isFirst) {
          bufferedOutputStream.write("\n".getBytes());
        }
        bufferedOutputStream.write(objectMapper.writeValueAsBytes(record));
        isFirst = false;
      }
    } catch (IOException e) {
      LOG.error("Error during streaming documents using Spring mode: {}", e.getMessage());
    }
  }
}
