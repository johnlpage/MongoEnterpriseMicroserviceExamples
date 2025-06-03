# language: en
@api @vehicle_inspection
Feature: Vehicle Inspection API Management

  As a system administrator or data consumer,
  I want to manage and retrieve vehicle inspection data
  So that I can ensure data integrity and access historical records.

  @post @one_inspection @sunny_day
  Scenario: Successfully save a single vehicle inspection
    Given a payload:
      """
      {
        "testid": 1001,
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
    When I send a POST request to "/api/inspection"
    Then the response status code should be 200
    And the response body should be empty

  @post @one_inspection @rainy_day
  Scenario: Fail to save a single vehicle inspection due to malformed JSON
    Given a payload:
      """
      {
        "testid": 1002,
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
    When I send a POST request to "/api/inspection"
    Then the response status code should be 400
    And the response body should contain "Bad Request"

  @get @by_id @sunny_day
  Scenario: Successfully retrieve a vehicle inspection by ID
    Given empty payload
    When I send a GET request to "/api/inspections/id/1001"
    Then the response status code should be 200
    And the response should contain "testid": 1001
    And the response should contain "vehicle.model": "Corolla"

  @get @by_id @rainy_day
  Scenario: Fail to retrieve a vehicle inspection by non-existent ID
    Given empty payload
    When I send a GET request to "/api/inspections/id/4002"
    Then the response status code should be 404
    And the response body should be empty