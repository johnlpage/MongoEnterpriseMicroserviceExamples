package com.johnlpage.memex.cucumber.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.memex.model.VehicleInspection;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Date;
import java.util.List;

public class VehicleInspectionKafkaConsumerSteps {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Given("{int} vehicle inspections records are sent to kafka with capacity {long}")
    public void givenSendKafkaVehicleInspectionRecords(int iterationCount, long capacity) throws JsonProcessingException {

        for(int i=0;i<iterationCount;i++) {
            VehicleInspection inspection = new VehicleInspection();
            inspection.setTestid(91000L+i);
            inspection.setTestdate(new Date());
            inspection.setTestclass("A"+i);
            inspection.setTestresult("PASS");
            inspection.setCapacity(capacity);

            String message = objectMapper.writeValueAsString(inspection);
            kafkaTemplate.send("test", message);
        }
    }

    @Then("verify {int} records are saved in mongo with capacity {long}")
    public void theResponseShouldBeAStreamOfValidJsonObjectsEachOnANewLine(int recordsCount, long capacity) {
        List<VehicleInspection> savedDocs = mongoTemplate.findAll(VehicleInspection.class);
        Assertions.assertThat(savedDocs).hasSize(recordsCount);
        Assertions.assertThat(savedDocs.stream().allMatch((vi)->vi.getCapacity().equals(capacity))).isTrue();
    }
}
