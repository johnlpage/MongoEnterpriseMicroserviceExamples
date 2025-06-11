package com.johnlpage.memex.cucumber.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.memex.cucumber.service.VehicleInspectionIdRangeValidator;
import com.johnlpage.memex.model.VehicleInspection;
import com.mongodb.client.result.DeleteResult;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
public class MongoPreConditionSteps {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleInspectionIdRangeValidator idRangeValidator;

    @Given("the following vehicle inspections exist:")
    public void givenVehicleInspectionsExist(DataTable dataTable) throws JsonProcessingException {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            String json = row.get("vehicleInspection");
            VehicleInspection inspection = objectMapper.readValue(json, VehicleInspection.class);
            Long testId = inspection.getTestid();
            assertNotNull(testId, "testid is expected to be part of the data input");

            idRangeValidator.validate(testId);
            Query query = Query.query(Criteria.where("_id").is(testId));

            Document updateDoc = new Document(objectMapper.convertValue(inspection, Map.class));
            Update update = Update.fromDocument(updateDoc);

            VehicleInspection existingInspection = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true), VehicleInspection.class);

            log.info("Upserted VehicleInspection with id {}: {}", testId, existingInspection);
        }
    }

    @Given("the vehicle inspections in range {long}-{long} do not exist")
    public void theFollowingVehicleInspectionsInRangeDoesNotExist(long startId, long endId) {
        idRangeValidator.validateRange(startId, endId);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").gte(startId).lte(endId));
        DeleteResult result = mongoTemplate.remove(query, VehicleInspection.class);

        if(result.getDeletedCount() > 0) {
            log.info("Removed VehicleInspection within a range {}-{}: {}", startId, endId, result.getDeletedCount());
        }
    }

    @Given("the vehicle inspection with id {long} does not exist")
    public void theFollowingVehicleInspectionDoesNotExist(long testId) {
        idRangeValidator.validate(testId);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(testId));
        DeleteResult result = mongoTemplate.remove(query, VehicleInspection.class);

        if(result.getDeletedCount() > 0) {
            log.info("Removed VehicleInspection with id {}: {}", testId, result.getDeletedCount());
        }
    }

    @Given("the following vehicle inspections do not exist:")
    public void givenTheFollowingVehicleInspectionsDoNotExist(DataTable dataTable) {
        for (Map<String, String> row : dataTable.asMaps()) {
            if (row.size() != 1) {
                throw new IllegalArgumentException("Only one column per row is supported in this step.");
            }

            String key = row.keySet().iterator().next();
            String value = row.get(key);

            long rangeStart = idRangeValidator.getRangeStart();
            long rangeEnd = idRangeValidator.getRangeEnd();

            Query query = Query.query(Criteria.where("_id").gte(rangeStart).lte(rangeEnd));

            if (key.equalsIgnoreCase("testid")) {
                long testid = Long.parseLong(value);
                idRangeValidator.validate(testid);
                query = Query.query(Criteria.where("_id").is(testid));
            } else {
                query.addCriteria(Criteria.where(key).is(value));
            }
            DeleteResult result = mongoTemplate.remove(query, VehicleInspection.class);

            if (result.getDeletedCount() > 0) {
                log.info("Removed VehicleInspection with {}: {} - Count: {}", key, value, result.getDeletedCount());
            }
        }
    }
}
