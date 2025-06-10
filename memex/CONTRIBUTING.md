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

### Code Coverage

We use **JaCoCo** to measure code coverage by our tests.
*   After running the tests (e.g., via `mvn verify` or `mvn clean test`), a code coverage report is generated.
*   You can find the HTML report at `target/site/jacoco/index.html`. Please review this report to ensure your contributions are adequately tested.

---

If you're planning to contribute, please familiarize yourself with these tools and ensure your changes include appropriate tests and maintain or improve code coverage.
