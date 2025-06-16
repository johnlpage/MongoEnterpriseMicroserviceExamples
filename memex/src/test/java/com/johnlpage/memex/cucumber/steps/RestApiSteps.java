package com.johnlpage.memex.cucumber.steps;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import com.johnlpage.memex.cucumber.service.MacrosRegister;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class RestApiSteps {

    @Value("${memex.base-url}")
    private String baseUrl;

    @Autowired
    MacrosRegister macroRegister;

    private Response response;

    @ParameterType("true|false")
    public Boolean bool(String bool) {
        return Boolean.parseBoolean(bool);
    }

    @When("I send a POST request to {string} with the payload:")
    public void iSendAPOSTRequestTo(String localUrl, String payload) {
        String processedUrl = macroRegister.replaceMacros(localUrl);
        response = given()
                .baseUri(baseUrl)
                .contentType(ContentType.JSON)
                .body(payload)
                .post(processedUrl);
    }

    @When("I send a GET request to {string}")
    public void userSendsGetRequest(String localUrl) {
        String processedUrl = macroRegister.replaceMacros(localUrl);
        response = given()
                .baseUri(baseUrl)
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
