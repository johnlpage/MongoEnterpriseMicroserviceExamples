package com.johnlpage.memex.cucumber.steps;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.memex.model.VehicleInspection;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class InspectionSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleInspectionIdRangeValidator idRangeValidator;

    private Response response;
    private ZonedDateTime capturedTimestamp;

    public String baseUrl() {
        return "http://localhost:" + port;
    }

    @ParameterType("true|false")
    public Boolean bool(String bool) {
        return Boolean.parseBoolean(bool);
    }

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

            mongoTemplate.upsert(query, update, VehicleInspection.class);

        }
    }

    @Given("the vehicle inspections in range {long}-{long} do not exist")
    public void theFollowingVehicleInspectionsInRangeDoesNotExist(long startId, long endId) {
        idRangeValidator.validateRange(startId, endId);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").gte(startId).lte(endId));
        mongoTemplate.remove(query, VehicleInspection.class);
    }

    @Given("the vehicle inspection with id {long} does not exist")
    public void theFollowingVehicleInspectionDoesNotExist(long testId) {
        idRangeValidator.validate(testId);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(testId));
        mongoTemplate.remove(query, VehicleInspection.class);
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
            mongoTemplate.remove(query, VehicleInspection.class);

        }
    }

    @Given("I capture the current timestamp")
    public void iCaptureTheCurrentTimestamp() {
        this.capturedTimestamp = ZonedDateTime.now();
    }

    @Given("I wait for {int} second(s)")
    public void iWaitForSeconds(int seconds) throws InterruptedException {
        TimeUnit.SECONDS.sleep(seconds);
    }

    @When("I send a POST request to {string} with the payload:")
    public void iSendAPOSTRequestTo(String localUrl, String payload) {
        String processedUrl = processUrl(localUrl);
        response = given()
                .baseUri(baseUrl())
                .contentType(ContentType.JSON)
                .body(payload)
                .post(processedUrl);
    }

    private String processUrl(String localUrl) {
        String processedUrl = localUrl;
        if (this.capturedTimestamp != null && processedUrl.contains("<timestamp>")) {
            String formattedTimestamp = this.capturedTimestamp.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            processedUrl = processedUrl.replace("<timestamp>", formattedTimestamp);
        }
        return processedUrl;
    }

    @When("I send a GET request to {string}")
    public void userSendsGetRequest(String localUrl) {
        String processedUrl = processUrl(localUrl);
        response = given()
                .baseUri(baseUrl())
                .get(processedUrl);
    }

    @Then("the response status code should be {int}")
    public void responseShouldHaveStatusCode(int expectedStatusCode) {
        assertNotNull(response, "Response should not be null");
        response.then().statusCode(expectedStatusCode);
    }

    @Then("the response should be empty")
    public void theResponseShouldBeEmpty() {
        assertNotNull(response, "Response should not be null");
        response.then().body(emptyString());
    }

    @Then("the response should contain {string}: {int}")
    public void responseShouldContainTestId(String key, int value) {
        assertNotNull(response.getBody(), "Response body should not be null");
        response.then().body(key, equalTo(value));
    }

    @Then("the response should contain {string}: {bool}")
    public void responseShouldContainTestId(String key, Boolean value) {
        assertNotNull(response.getBody(), "Response body should not be null");
        response.then().body(key, equalTo(value));
    }

    @Then("the response should contain {string}: {string}")
    public void responseShouldContainKeyValue(String key, String value) {
        assertNotNull(response.getBody(), "Response body should not be null");
        response.then().body(key, equalTo(value));
    }

    @Then("the response should contain {string}")
    public void responseShouldContain(String expectedSubstring) {
        assertNotNull(response.getBody(), "Response body should not be null");
        response.then().body(containsString(expectedSubstring));
    }

    @Then("the response should contain {string} with {int} items")
    public void responseShouldContainContentWithItems(String key, int expectedCount) {
        assertNotNull(response.getBody(), "Response body should not be null");
        response.then().body(key + ".size()", equalTo(expectedCount));
    }

    @Then("the response should be a non empty JSON array")
    public void theResponseShouldBeANonEmptyJsonArray() {
        assertNotNull(response.getBody(), "Response body should not be null");
        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> list = jsonPath.getList("$");
        assertNotNull(list, "Response should be a JSON array");
        assertFalse(list.isEmpty(), "Response array should not be empty");
    }

    @Then("each item in the response array should contain {string}: {string}")
    public void eachItemInTheResponseArrayShouldContain(String key, String value) {
        assertNotNull(response.getBody(), "Response body should not be null");
        JsonPath jsonPath = response.jsonPath();
        List<Map<String, Object>> list = jsonPath.getList("$");
        assertFalse(list.isEmpty(), "Response array should not be empty for this check");
        for (Map<String, Object> item : list) {
            if (key.indexOf('.') > 0) {
                // If the value is a vehicle field, we need to check the nested structure
                String[] parts = key.split("\\.");
                assertThat(item, hasKey(parts[0]));
                Object nestedValue = item.get(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    if (nestedValue instanceof Map) {
                        nestedValue = ((Map<?, ?>) nestedValue).get(parts[i]);
                    } else {
                        nestedValue = null;
                        break;
                    }
                }
                assertThat(nestedValue, equalTo(value));
            } else {
                // Otherwise, check the direct key-value pair
                assertThat(item, hasKey(key));
                assertThat(item.get(key), equalTo(value));
            }
        }
    }

    @Then("the {string} header should be {string}")
    public void theContentTypeHeaderShouldBe(String header, String expectedContentType) {
        assertNotNull(response, "Response should not be null");
        response.then().header(header, expectedContentType);
    }

    @Then("the response should be a stream of valid JSON objects, each on a new line")
    public void theResponseShouldBeAStreamOfValidJsonObjectsEachOnANewLine() {
        assertNotNull(response, "Response should not be null");
        String body = response.getBody().asString();
        assertNotNull(body, "Response body should not be null");
        assertFalse(body.isEmpty(), "Response body should not be empty for stream check");

        // Split by any standard newline character(s)
        String[] lines = body.split("\\r?\\n");
        assertTrue(lines.length > 0, "Response body should contain at least one line for stream check");

        for (String line : lines) {
            // Skip potentially empty lines that might result from splitting (e.g., trailing newline)
            if (line.trim().isEmpty()) continue;
            try {
                JsonPath.from(line); // This will throw an exception if the line is not valid JSON
            } catch (Exception e) {
                fail("Line is not a valid JSON object: '" + line + "'. Error: " + e.getMessage());
            }
        }
    }
}
