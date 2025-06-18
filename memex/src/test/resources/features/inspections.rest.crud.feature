@restapi @vehicle_inspection
Feature: Vehicle Inspection REST API - Core CRUD Operations
  This feature outlines the core Create, Read, Update, and Delete (CRUD) operations for vehicle inspections via the REST API.
  It includes tests for creating new inspections, retrieving existing ones by ID or other criteria, and handling various success and failure scenarios.

  @post @one_inspection @sunny_day
  Scenario: Successfully save a single vehicle inspection
    Given the vehicle inspection with id 10001 does not exist
    When I send a POST request to "/api/inspection" with the payload:
      """
      {
        "testid": 10001,
        "testdate": "2023-10-26T10:00:00Z",
        "testclass": "Class 1",
        "testtype": "Annual",
        "testresult": "PASS",
        "testmileage": 50000,
        "postcode": "SW1A 0AA",
        "fuel": "Petrol",
        "capacity": 1.6,
        "firstusedate": "2018-01-15T00:00:00Z",
        "faileditems": [],
        "vehicle": {
          "make": "Toyota",
          "model": "Corolla",
          "year": 2018,
          "vin": "VIN1234567890ABCDE"
        }
      }
      """
    Then the response status code should be 200
    And the response should be empty

  @post @one_inspection @rainy_day
  Scenario: Fail to save a single vehicle inspection due to malformed JSON
    Given the vehicle inspection with id 10002 does not exist
    When I send a POST request to "/api/inspection" with the payload:
      """
      {
        "testid": 10002,
        "testdate": "2023-10-26T10:00:00Z",
        "testclass": "Class 1",
        "testtype": "Annual",
        "testresult": "PASS",
        "testmileage": 50000,
        "postcode": "SW1A 0AA",
        "fuel": "Petrol",
        "capacity": 1.6,
        "firstusedate": "2018-01-15T00:00:00Z",
        "faileditems": [],
        "vehicle": {
          "make": "Toyota",
          "model": "Corolla",
          "year": 2018,
          "vin": "VIN1234567890ABCDE"
        // Missing closing brace for vehicle object
      }
      """
    Then the response status code should be 400
    And the response should contain "Bad Request"

  @get @by_id @sunny_day
  Scenario: Successfully retrieve a vehicle inspection by ID
    Given the following vehicle inspections exist:
      | vehicleinspection                                  |
      | {"testid": 10001, "vehicle": {"model": "Corolla"}} |
    When I send a GET request to "/api/inspections/id/10001"
    Then the response status code should be 200
    And the response should contain "testid": 10001
    And the response should contain "vehicle.model": "Corolla"

  @get @by_id @rainy_day
  Scenario: Fail to retrieve a vehicle inspection by non-existent ID
    Given the vehicle inspection with id 10001 does not exist
    When I send a GET request to "/api/inspections/id/10001"
    Then the response status code should be 404
    And the response should be empty

  @get @by_model @sunny_day
  Scenario Outline: Successfully retrieve vehicle inspections by model with pagination
    Given the following vehicle inspections exist:
      | vehicleinspection                                |
      | {"testid": 10002, "vehicle": {"model": "Focus"}} |
      | {"testid": 10004, "vehicle": {"model": "Focus"}} |
      | {"testid": 10006, "vehicle": {"model": "Focus"}} |
      | {"testid": 10008, "vehicle": {"model": "Focus"}} |
    When I send a GET request to "/api/inspections/model/<model>?page=<page>&size=<size>"
    Then the response status code should be 200
    And the response should contain "content" with <expected_count> items
    And the response should contain "pageNumber": <page>
    And the response should contain "pageSize": <size>

    Examples:
      | model | page | size | expected_count |
      | Focus | 0    | 3    | 3              |
      | Focus | 1    | 3    | 1              |
      | Focus | 0    | 10   | 4              |

  @get @by_model @sunny_day
  Scenario: Retrieve no vehicle inspections for a non-existent model
    Given the following vehicle inspections do not exist:
      | vehicleinspection                     |
      | {"vehicle.model": "NonExistentModel"} |
    When I send a GET request to "/api/inspections/model/NonExistentModel"
    Then the response status code should be 200
    And the response should contain "content" with 0 items
    And the response should contain "pageNumber": 0
    And the response should contain "pageSize": 10
