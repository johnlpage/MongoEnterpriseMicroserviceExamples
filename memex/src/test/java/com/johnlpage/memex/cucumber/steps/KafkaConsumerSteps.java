package com.johnlpage.memex.cucumber.steps;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.memex.cucumber.service.VehicleInspectionIdRangeValidator;
import com.johnlpage.memex.model.VehicleInspection;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.annotation.PostConstruct;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class KafkaConsumerSteps {

    @Value("${memex.base-url}")
    private String apiBaseUrl;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private RestClient.Builder restClientBuilder;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        this.restClient = restClientBuilder.baseUrl(apiBaseUrl).build();
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleInspectionIdRangeValidator idRangeValidator;

    @When("I send {int} vehicle inspections starting with id {long} to kafka {string} topic with:")
    public void sendVehicleInspectionsToKafka(int count, long startId, String topicName, String jsonTemplate) throws JsonProcessingException {
        idRangeValidator.validate(startId);
        long endIdInclusive = startId + count - 1;
        idRangeValidator.validate(endIdInclusive);

        for (int i = 0; i < count; i++) {
            long testId = startId + i;
            VehicleInspection vehicleInspection = objectMapper.readValue(jsonTemplate, VehicleInspection.class);
            vehicleInspection.setTestid(testId);

            String message = objectMapper.writeValueAsString(vehicleInspection);
            kafkaTemplate.send(topicName, message);
        }
    }

    @Then("{int} vehicle inspections starting from id {long} do exist with:")
    public void verifyVehicleInspectionsSaved(int count, long startId, String expectedJson) throws JsonProcessingException {
        long endId = startId + count - 1;
        idRangeValidator.validateRange(startId, endId);

        JsonNode expectedNode = objectMapper.readTree(expectedJson);

        String rangeCheck = "\"_id\": {\"$gte\": " + startId + ", \"$lte\": " + endId + "}";
        String mongoQueryJson = String.format("{\"filter\": {%s}}", rangeCheck);

        ResponseEntity<List<VehicleInspection>> responseEntity = restClient.post()
                .uri(apiBaseUrl + "/api/inspections/query")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mongoQueryJson)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<VehicleInspection>>() {
                });

        assertTrue(responseEntity.getStatusCode().is2xxSuccessful());
        List<VehicleInspection> inspections = responseEntity.getBody();

        assertNotNull(inspections);
        assertEquals(count, inspections.size());
        inspections.forEach((inspection) -> {
            JsonNode actualJson = null;
            try {
                actualJson = objectMapper.readTree(objectMapper.writeValueAsString(inspection));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Vehicle inspection verification failed for testid: " + inspection.getTestid(), e);
            }
            assertJsonContains(expectedNode, actualJson);
        });
    }

    private void assertJsonContains(JsonNode expected, JsonNode actual) {
        for (Iterator<Map.Entry<String, JsonNode>> it = expected.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            String fieldName = field.getKey();
            JsonNode expectedValue = field.getValue();
            JsonNode actualValue = actual.get(fieldName);

            Assertions.assertThat(actualValue)
                    .withFailMessage("Expected field '%s' to exist", fieldName)
                    .isNotNull();

            if (expectedValue.isObject()) {
                assertJsonContains(expectedValue, actualValue);
            } else {
                Assertions.assertThat(actualValue).isEqualTo(expectedValue);
            }
        }
    }
}