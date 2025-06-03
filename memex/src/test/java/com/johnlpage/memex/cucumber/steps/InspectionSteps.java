package com.johnlpage.memex.cucumber.steps;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import com.johnlpage.memex.MemexApplication;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@CucumberContextConfiguration
@SpringBootTest(classes = MemexApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class InspectionSteps {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void containersProperties(DynamicPropertyRegistry registry) {
        mongoDBContainer.start();
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> MEMEX);
    }

    public static final String MEMEX = "memex";

    @LocalServerPort
    private int port;

    private Response response;
    private String payload;

    public String baseUrl() {
        return "http://localhost:" + port;
    }

    @ParameterType("true|false")
    public Boolean bool(String bool){
        return Boolean.parseBoolean(bool);
    }

    @Given("a payload:")
    public void givenPayload(String payload) {
        this.payload = payload;
    }

    @Given("the following vehicle inspections exist:")
    public void givenVehicleInspectionsExist(DataTable dataTable) {
        // test assumes it exists however it would be better to create it here
    }

    @Given("the following vehicle inspections do not exist:")
    public void givenVehicleInspectionsDoNotExist(DataTable dataTable) {
        // test assumes it does not exist however it would be better to delete it here
    }

    @When("I send a POST request to {string}")
    public void iSendAPOSTRequestTo(String localUrl) {
        response = given()
                .baseUri(baseUrl())
                .contentType(ContentType.JSON)
                .body(payload)
                .post(localUrl);
    }

    @When("I send a GET request to {string}")
    public void userSendsGetRequest(String localUrl) {
        response = given()
                .baseUri(baseUrl())
                .get(localUrl);
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
            if(key.indexOf('.') > 0) {
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
}
