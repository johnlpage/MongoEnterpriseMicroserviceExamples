package com.johnlpage.memex.cucumber.steps;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.johnlpage.memex.MemexApplication;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.response.Response;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
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

    @When("the user sends GET request to {string}")
    public void userSendsGetRequest(String localUrl) {
        response = given()
                .baseUri(baseUrl())
                .get(localUrl);
    }

    @Then("the response should have status code {int}")
    public void responseShouldHaveStatusCode(int expectedStatusCode) {
        assertNotNull(response, "Response should not be null");
        response.then().statusCode(expectedStatusCode);
    }

    @Then("the response should contain the Vehicle Inspection with id {int}")
    public void responseShouldContainVehicleInspectionWithId(int inspectionId) {
        assertNotNull(response.getBody(), "Response body should not be null");
        response.then().body("id", equalTo(inspectionId));
    }
}
