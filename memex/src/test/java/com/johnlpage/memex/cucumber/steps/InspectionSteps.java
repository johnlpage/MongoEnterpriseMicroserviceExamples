package com.johnlpage.memex.cucumber.steps;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.johnlpage.memex.MemexApplication;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import static io.restassured.RestAssured.given;

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

    @LocalServerPort
    private int port;

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void containersProperties(DynamicPropertyRegistry registry) {
        mongoDBContainer.start();
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> "mews");
    }

    public String baseUrl() {
        return "http://localhost:" + port;
    }

    private Response response;
    private String payload;

    @Given("a payload:")
    public void givenPayload(String payload) {
        this.payload = payload;
    }

    @Given("empty payload")
    public void givenEmptyPayload() {
        this.payload = null;
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

    @Then("the response body should be empty")
    public void theResponseBodyShouldBeEmpty() {
        assertNotNull(response, "Response should not be null");
        response.then().body(emptyString());
    }

    @Then("the response should contain {string}: {int}")
    public void responseShouldContainTestId(String key, int testIdValue) {
        assertNotNull(response.getBody(), "Response body should not be null");
        response.then().body(key, equalTo(testIdValue));
    }

    @Then("the response should contain {string}: {string}")
    public void responseShouldContainKeyValue(String key, String value) {
        assertNotNull(response.getBody(), "Response body should not be null");
        response.then().body(key, equalTo(value));
    }

    @Then("the response body should contain {string}")
    public void responseBodyShouldContain(String expectedSubstring) {
        assertNotNull(response.getBody(), "Response body should not be null");
        response.then().body(containsString(expectedSubstring));
    }
}
