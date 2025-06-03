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
    And the response should be empty

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
    And the response should contain "Bad Request"

  @get @by_id @sunny_day
  Scenario: Successfully retrieve a vehicle inspection by ID
    Given the following vehicle inspections exist:
      | testid | vehicle.model   |
      | 1001   | Corolla         |
    When I send a GET request to "/api/inspections/id/1001"
    Then the response status code should be 200
    And the response should contain "testid": 1001
    And the response should contain "vehicle.model": "Corolla"

  @get @by_id @rainy_day
  Scenario: Fail to retrieve a vehicle inspection by non-existent ID
    Given the following vehicle inspections do not exist:
      | testid |
      | 4002   |
    When I send a GET request to "/api/inspections/id/4002"
    Then the response status code should be 404
    And the response should be empty

  @post @load_stream @sunny_day
  Scenario Outline: Successfully load a stream of vehicle inspections with different update strategies and futz options
    Given a payload:
      """
      [
        {
          "testid": <id1>,
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
        },
        {
          "testid": <id2>,
          "testdate": "2023-10-27T11:00:00Z",
          "testclass": "Class 2",
          "testtype": "Interim",
          "testresult": "FAIL",
          "testmileage": 60000,
          "postcode": "SW1A 0AB",
          "fuel": "Diesel",
          "capacity": 2.0,
          "firstusedate": "2019-03-20T00:00:00Z",
          "faileditems": ["Brakes", "Lights"],
          "vehicle": {
            "make": "Ford",
            "model": "Focus",
            "year": 2019,
            "vin": "VIN0987654321FEDCBA"
          }
        }
      ]
      """
    When I send a POST request to "/api/inspections?updateStrategy=<strategy>&futz=<futz>"
    Then the response status code should be 200
    And the response should contain "inserts": 2
    And the response should contain "success": true

    Examples:
      | strategy        | futz  | id1  | id2  |
      | REPLACE         | false | 2001 | 2002 |
      | UPDATE          | false | 2003 | 2004 |
      | UPDATEWITHHISTORY | true  | 2005 | 2006 |
      | REPLACE         | true  | 2007 | 2008 |

  @post @load_stream @rainy_day
  Scenario: Fail to load a stream of vehicle inspections due to invalid JSON
    Given a payload:
      """
      [
        {
          "testid": 3001,
          "testdate": "2023-10-26T10:00:00Z"
        },
        { // Malformed object, missing closing brace
          "testid": 3002,
          "testdate": "2023-10-27T11:00:00Z"
      ]
      """
    When I send a POST request to "/api/inspections?updateStrategy=REPLACE"
    Then the response status code should be 200
    And the response should contain "success": false
    And the response should contain "Unexpected character"

  @get @by_model @sunny_day
  Scenario Outline: Successfully retrieve vehicle inspections by model with pagination
    Given the following vehicle inspections exist:
      | testid | vehicle.model   |
      | 2002   | Focus           |
      | 2004   | Focus           |
      | 2006   | Focus           |
      | 2008   | Focus           |
    When I send a GET request to "/api/inspections/model/<model>?page=<page>&size=<size>"
    Then the response status code should be 200
    And the response should contain "content" with <expected_count> items
    And the response should contain "pageNumber": <page>
    And the response should contain "pageSize": <size>

    Examples:
      | model   | page | size | expected_count |
      | Focus   | 0    | 3    | 3              |
      | Focus   | 1    | 3    | 1              |
      | Focus   | 0    | 10   | 4              |

  @get @by_model @sunny_day
  Scenario: Retrieve no vehicle inspections for a non-existent model
    Given the following vehicle inspections do not exist:
      | model              |
      | NonExistentModel   |
    When I send a GET request to "/api/inspections/model/NonExistentModel"
    Then the response status code should be 200
    And the response should contain "content" with 0 items
    And the response should contain "pageNumber": 0
    And the response should contain "pageSize": 10

  @post @mongo_query @sunny_day
  Scenario: Successfully execute a native MongoDB query
    Given the following vehicle inspections exist:
      | testid | vehicle.make |
      | 1001   | Toyota       |
      | 2002   | Ford         |
    And a payload:
      """
      {
        "filter": {
          "vehicle.make": "Toyota"
         }
      }
      """
    When I send a POST request to "/api/inspections/query"
    Then the response status code should be 200
    And the response should be a non empty JSON array
    And each item in the response array should contain "vehicle.make": "Toyota"

  @post @mongo_query @rainy_day
  Scenario: Fail to execute a native MongoDB query due to invalid syntax
    Given a payload:
      """{
        "filter": {
          "$invalid_operator": "value"
        }
      }
      """
    When I send a POST request to "/api/inspections/query"
    Then the response status code should be 500
    And the response should contain "Internal Server Error"