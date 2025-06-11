package com.johnlpage.memex.cucumber.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.johnlpage.memex.model.VehicleInspection;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.assertj.core.api.Assertions;
import org.bson.Document;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class VehicleInspectionKafkaConsumerSteps {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleInspectionIdRangeValidator idRangeValidator;

    @When("I send {int} vehicle inspections starting with id {long} to kafka with:")
    public void sendVehicleInspectionsToKafka(int count, long startId, String jsonTemplate) throws JsonProcessingException {
        idRangeValidator.validate(startId);
        long endIdInclusive = startId + count - 1;
        idRangeValidator.validate(endIdInclusive);

        for (int i = 0; i < count; i++) {
            long testId = startId + i;
            VehicleInspection vehicleInspection = objectMapper.readValue(jsonTemplate, VehicleInspection.class);
            vehicleInspection.setTestid(testId);

            String message = objectMapper.writeValueAsString(vehicleInspection);
            kafkaTemplate.send("test", message);
        }
    }

    @Then("verify {int} vehicle inspections are saved starting from id {long} in mongo with:")
    public void verifyVehicleInspectionsSaved(int count, long startId, String expectedJson) throws JsonProcessingException {
        long endId = startId + count - 1;
        idRangeValidator.validateRange(startId, endId);

        JsonNode expectedNode = objectMapper.readTree(expectedJson);

        Query query = Query.query(Criteria.where("testid").gte(startId).lte(endId));
        List<VehicleInspection> inspections = mongoTemplate.find(query, VehicleInspection.class);

        Assertions.assertThat(inspections).hasSize(count);

        for (VehicleInspection inspection : inspections) {
            JsonNode inspectionJson = objectMapper.readTree(objectMapper.writeValueAsString(inspection));
            assertJsonContains(expectedNode, inspectionJson);
        }
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
