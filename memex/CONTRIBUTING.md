# Contributing to Memex

First off, thank you for considering contributing to Memex! Your help is appreciated.

## Guiding Principles

*   **All contributions must be covered by tests.** This ensures the stability and reliability of the application.
*   Aim for high code coverage for any new or modified code.
*   Follow the existing code style and conventions.

## Testing Approach

This project uses a Behavior-Driven Development (BDD) approach for testing, primarily focusing on API-level integration tests. The technology stack for testing includes:

*   **[Cucumber](https://cucumber.io/)**: Used for writing high-level test scenarios in a human-readable format called Gherkin (files with `.feature` extension). These scenarios describe the behavior of the application from a user's perspective.
    *   Feature files are located in `src/test/resources/features/`.
    *   Step definitions, which implement the Gherkin steps, are Java methods located in classes within `src/test/java/com/johnlpage/memex/cucumber/steps/`.

*   **[JUnit 5](https://junit.org/junit5/)**: The foundational testing framework used to run the Cucumber tests and provide assertions. It's the default low-level testing framework for modern Java applications, including those built with Spring Boot 3.

*   **[Rest Assured](https://rest-assured.io/)**: A Java library used within the Cucumber step definitions to test RESTful APIs. It provides a fluent and intuitive DSL for making HTTP requests and validating responses, leading to readable and concise test code.

*   **[Testcontainers](https://www.testcontainers.org/)**: Used to manage external dependencies for tests, specifically a MongoDB instance. This ensures tests run in a consistent and isolated environment.

### Test Structure and Execution Flow

1.  **Feature Files (`.feature`)**: Define test scenarios using Gherkin syntax (Given-When-Then). These describe the preconditions, actions, and expected outcomes.
2.  **Step Definitions (`.java`)**: Java methods annotated with Cucumber annotations (`@Given`, `@When`, `@Then`) that map Gherkin steps to executable code.
    *   `@Given` steps often involve setting up preconditions, such as populating the MongoDB database with specific test data using `MongoTemplate`. This database is typically managed by Testcontainers.
    *   `@When` steps typically use Rest Assured to send HTTP requests to the application's API endpoints.
    *   `@Then` steps use Rest Assured's validation capabilities, along with JUnit 5 assertions and Hamcrest matchers, to verify the HTTP response (status code, headers, body content).
3.  **Test Runner**: JUnit 5 is used to discover and execute the Cucumber tests.

### MongoDB for Tests (Testcontainers)

For integration tests requiring a database, we use Testcontainers to spin up a MongoDB instance.

*   **Default Behavior**: If the `spring.data.mongodb.uri` property in `src/test/resources/application-test.properties` is empty (which is the default), the tests will automatically attempt to start a MongoDB container using Docker.
    *   **Requirement**: A Docker daemon must be running locally for this to work (e.g., by starting Docker Desktop).
*   **Using an External MongoDB**: If you do not have Docker available, or wish to use a specific external MongoDB instance for testing, you can specify its connection string by setting the `spring.data.mongodb.uri` property in `src/test/resources/application-test.properties`. In this case, Testcontainers will not attempt to start a local MongoDB container.

### Test Data `testid` Range

To ensure test data isolation and prevent unintended side effects, vehicle inspection `testid` values used during test setup and execution **must** fall within a dedicated range. This range is defined by the following properties in `src/test/resources/application-test.properties`:
*   `memex.test.data.vehicleinspection-testid-range.start`
*   `memex.test.data.vehicleinspection-testid-range.end`

**Important:**
*   When writing or modifying tests, ensure all `testid` values used for creating, querying, or deleting test data are within this configured range.
*   Tests **must not** attempt to read, modify, or delete data outside of this dedicated test ID range. This is crucial for preventing tests from interfering with each other or with any non-test data.
*   Using a `testid` outside of this range during test data manipulation will lead to test failures.

**Caution**: If you configure the tests to run against a real (non-Testcontainer) MongoDB instance, be aware that the tests **will modify data within this `testid` range on that MongoDB instance.** Ensure this range does not overlap with critical data if using a shared or persistent MongoDB server for testing. It is generally recommended to use Testcontainers or a dedicated, ephemeral test database to avoid accidental data modification.

These properties can be adjusted in `src/test/resources/application-test.properties` if necessary for specific testing needs, but ensure they define a sensible range and that all tests strictly adhere to operating only within the specified `testid` boundaries.

## Adding a New Test

To add a new Cucumber test, follow these general steps:

1.  **Define Scenarios in a `.feature` file**:
    *   Locate an existing relevant `.feature` file in `src/test/resources/features/` (e.g., `inspections.rest.crud.feature`, `inspections.kafka.feature`) or create a new one if testing a new feature area or interaction type.
    *   Write your test scenarios using Gherkin syntax (Given/When/Then). Clearly describe the preconditions, actions to be performed, and expected outcomes.
    *   Use tags (`@tagname`) to categorize your scenarios or features (e.g., `@restapi`, `@kafka`, `@sunny_day`).

2.  **Implement Step Definitions**:
    *   For each Gherkin step in your scenario, you'll need a corresponding Java method in a step definition class. These classes are organized by concern within `src/test/java/com/johnlpage/memex/cucumber/steps/`:
        *   `MongoPreConditionSteps.java`: For steps setting up or verifying database state (`@Given`).
        *   `RestApiSteps.java`: For steps interacting with the REST API (`@When`, `@Then`).
        *   `TimeManagementSteps.java`: For steps related to time (capturing timestamps, waiting).
        *   `KafkaConsumerSteps.java`: For steps interacting with Kafka consumers/topics.
        *   If your new steps cover a distinct area of functionality that doesn't fit well into any of the existing `Steps` classes above (e.g., interacting with a new external service, or a completely different domain of application logic), it's appropriate to create a new Java class for these step definitions within the `src/test/java/com/johnlpage/memex/cucumber/steps/` package. Ensure it follows the same patterns for dependency injection (e.g., `@Autowired` fields) and Spring configuration if needed.
    *   **Reuse existing steps**: Before writing new step definition methods, check if existing ones in the relevant files can be reused.
    *   **Create new steps**: If a step is new, add a public method annotated with `@Given`, `@When`, or `@Then` and a regex matching your Gherkin step to the appropriate class:
        *   **Data Setup (`@Given` in `MongoPreConditionSteps.java`)**: 
        
            Use `MongoTemplate` for direct database interaction.
            Crucially, ensure any `testid` values for `VehicleInspection` data are validated using the injected `VehicleInspectionIdRangeValidator` to adhere to the configured range in `application-test.properties`.
        *   **API Interaction (`@When`/`@Then` in `RestApiSteps.java`)**:
            
            Use Rest Assured to build, send HTTP requests, and validate responses.
            Utilize the injected `MacrosRegister` if your Gherkin step includes URL placeholders (e.g., `<timestamp>`) that need to be dynamically replaced.

3.  **Run Your Test**:
    *   Execute the tests (e.g., using `mvn clean verify` or by running the specific feature/scenario from your IDE).
    *   Ensure your new test passes and doesn't break existing tests.

4.  **Check Code Coverage**:
    *   After your tests pass, review the JaCoCo code coverage report (see "Code Coverage" section below) to ensure your changes are adequately covered.

### Code Coverage

We use **JaCoCo** to measure code coverage by our tests.
*   After running the tests (e.g., via `mvn verify` or `mvn clean test`), a code coverage report is generated.
*   You can find the HTML report at `target/site/jacoco/index.html`. Please review this report to ensure your contributions are adequately tested.

---

If you're planning to contribute, please familiarize yourself with these tools and ensure your changes include appropriate tests and maintain or improve code coverage.
