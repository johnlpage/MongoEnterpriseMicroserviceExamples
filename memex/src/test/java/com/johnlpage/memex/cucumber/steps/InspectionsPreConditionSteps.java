package com.johnlpage.memex.cucumber.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.memex.cucumber.service.VehicleInspectionIdRangeValidator;
import com.johnlpage.memex.model.VehicleInspection;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
public class InspectionsPreConditionSteps {

    @Value("${memex.base-url}")
    private String apiBaseUrl;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final VehicleInspectionIdRangeValidator idRangeValidator;

    @Autowired
    public InspectionsPreConditionSteps(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                                        VehicleInspectionIdRangeValidator idRangeValidator) {
        this.restClient = restClientBuilder.baseUrl(apiBaseUrl).build();
        this.objectMapper = objectMapper;
        this.idRangeValidator = idRangeValidator;
    }

    @Given("the following vehicle inspections exist:")
    public void givenVehicleInspectionsExist(DataTable dataTable) throws JsonProcessingException {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        List<VehicleInspection> inspections = new ArrayList<>();

        for (Map<String, String> row : rows) {
            String json = row.get("vehicleinspection");
            VehicleInspection inspection = objectMapper.readValue(json, VehicleInspection.class);
            inspections.add(inspection);
            Long testId = inspection.getTestid();
            assertNotNull(testId, "testid is expected to be part of the data input");
            idRangeValidator.validate(testId);
        }

        upsertInspectionsViaApi(inspections);
    }

    @Given("the vehicle inspection with id {long} does not exist")
    public void theFollowingVehicleInspectionDoesNotExist(long testId) throws JsonProcessingException {
        idRangeValidator.validate(testId);
        deleteInspectionsViaApi(Collections.singletonList(testId));
    }

    @Given("the vehicle inspections in range {long}-{long} do not exist")
    public void theFollowingVehicleInspectionsInRangeDoesNotExist(long startId, long endId) throws JsonProcessingException {
        idRangeValidator.validateRange(startId, endId);
        List<Long> idsForDeletion = LongStream.rangeClosed(startId, endId)
                .boxed()
                .collect(Collectors.toList());

        deleteInspectionsViaApi(idsForDeletion);
    }

    @Given("the following vehicle inspections do not exist:")
    public void givenTheFollowingVehicleInspectionsDoNotExist(DataTable dataTable) throws JsonProcessingException {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        List<Long> idsToDelete = new ArrayList<>();

        for (Map<String, String> row : rows) {
            if (row.size() != 1) {
                throw new IllegalArgumentException("Only one column per row is supported in this step for deletion criteria.");
            }
            Map.Entry<String, String> entry = row.entrySet().iterator().next();
            String key = entry.getKey();
            String inputQuery = entry.getValue();

            String rangeCheck = "\"_id\": {\"$gte\": " + idRangeValidator.getRangeStart() + ", \"$lte\": " + idRangeValidator.getRangeEnd() + "}";
            String mongoQueryJson = String.format("{\"filter\": {%s, %s}}", rangeCheck, inputQuery.substring(inputQuery.indexOf('{') + 1, inputQuery.lastIndexOf('}'))); // Basic example, might need more robust query building

            try {
                ResponseEntity<List<VehicleInspection>> responseEntity = restClient.post()
                        .uri(apiBaseUrl + "/api/inspections/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mongoQueryJson)
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<List<VehicleInspection>>() {
                        });

                if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                    List<VehicleInspection> inspections = responseEntity.getBody();
                    inspections.forEach(insp -> idsToDelete.add(insp.getTestid()));
                } else {
                    log.warn("Query {} returned status {} or empty body. No IDs added for deletion.",
                            mongoQueryJson, responseEntity.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Error querying API for a query {}", mongoQueryJson, e);
                throw new RuntimeException("Error querying API for a query: " + mongoQueryJson, e);
            }
        }

        deleteInspectionsViaApi(idsToDelete);
    }

    private void deleteInspectionsViaApi(List<Long> idsToDelete) throws JsonProcessingException {
        if (idsToDelete.isEmpty()) {
            log.info("Delete IDs list is empty, skipping API call.");
            return;
        }

        List<Map<String, Object>> payload = idsToDelete.stream()
                .map(id -> Map.<String, Object>of("testid", id, "deleted", true))
                .collect(Collectors.toList());

        String jsonPayload = objectMapper.writeValueAsString(payload);

        try {
            log.info("apiBaseUrl: {}", apiBaseUrl);
            ResponseEntity<String> response = restClient.post()
                    .uri(apiBaseUrl + "/api/inspections?updateStrategy=UPDATEWITHHISTORY&futz=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonPayload)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully marked inspections for deletion. IDs count: {}", idsToDelete.size());
            } else {
                log.error("Failed to mark inspections for deletion. Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("Failed to mark inspections for deletion: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling API to mark inspections for deletion. IDs count: {}", idsToDelete.size(), e);
            throw new RuntimeException("Error calling API to mark inspections for deletion: " + e.getMessage());
        }
    }

    private void upsertInspectionsViaApi(List<VehicleInspection> inspections) throws JsonProcessingException {
        if (inspections.isEmpty()) {
            log.info("No inspections to upsert, skipping API call.");
            return;
        }

        String jsonPayload = objectMapper.writeValueAsString(inspections);

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(apiBaseUrl + "/api/inspections?updateStrategy=UPDATEWITHHISTORY&futz=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonPayload)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully upserted {} inspections.", inspections.size());
            } else {
                log.error("Failed to upsert inspections. Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("Failed to upsert inspections: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling API to upsert inspections. Count: {}", inspections.size(), e);
            throw new RuntimeException("Error calling API to upsert inspections: " + e.getMessage());
        }
    }
}
