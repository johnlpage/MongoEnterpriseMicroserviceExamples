@restapi @streams @vehicle_inspection
Feature: Vehicle Inspection REST API - Data Streaming Capabilities
  This feature focuses on the data streaming capabilities of the REST API for vehicle inspections.
  It includes tests for loading multiple inspections in a single stream request and for streaming out existing inspection data in JSON format.

  @post @load_stream @sunny_day
  Scenario Outline: Successfully load a stream of vehicle inspections with different update strategies and futz options
    Given the vehicle inspections in range 10001-10008 do not exist
    When I send a POST request to "/api/inspections?updateStrategy=<strategy>&futz=<futz>" with the payload:
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
          "capacity": 56,
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
          "capacity": 76,
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
    Then the response status code should be 200
    And the response should contain "inserts": 2
    And the response should contain "success": true

    Examples:
      | strategy          | futz  | id1   | id2   |
      | REPLACE           | false | 10001 | 10002 |
      | UPDATE            | false | 10003 | 10004 |
      | UPDATEWITHHISTORY | true  | 10005 | 10006 |
      | REPLACE           | true  | 10007 | 10008 |

  @post @load_stream @sunny_day
  Scenario: Successfully delete a vehicle inspection
    Given the following vehicle inspections exist:
      | vehicleinspection |
      | {"testid": 10007} |
    When I send a POST request to "/api/inspections?updateStrategy=UPDATEWITHHISTORY&futz=true" with the payload:
      """
      [
        {
          "testid": 10007,
          "deleted": true
        }
      ]
      """
    Then the response status code should be 200
    And the response should contain "deletes": 1
    And the response should contain "success": true
    When I send a GET request to "/api/inspections/id/10007"
    Then the response status code should be 404

  @post @load_stream @rainy_day
  Scenario: Fail to load a stream of vehicle inspections due to invalid JSON
    Given the vehicle inspections in range 10001-10002 do not exist
    When I send a POST request to "/api/inspections?updateStrategy=REPLACE" with the payload:
      """
      [
        {
          "testid": 10001,
          "testdate": "2023-10-26T10:00:00Z"
        },
        { // Malformed object, missing closing brace
          "testid": 10002,
          "testdate": "2023-10-27T11:00:00Z"
      ]
      """
    Then the response status code should be 200
    And the response should contain "success": false
    And the response should contain "Unexpected character"

  @get @stream_json @sunny_day
  Scenario: Successfully stream all vehicle inspections as JSON
    Given the following vehicle inspections exist:
      | vehicleinspection                                |
      | {"testid": 10001, "vehicle": {"make": "Toyota"}} |
      | {"testid": 10002, "vehicle": {"make": "Ford"}}   |
    When I send a GET request to "/api/inspections/json"
    Then the response status code should be 200
    And the "Content-Type" header should be "application/json"
    And the "Transfer-Encoding" header should be "chunked"
    And the response should be a stream of valid JSON objects, each on a new line

  @get @stream_json_native @sunny_day
  Scenario: Successfully stream all vehicle inspections as native JSON
    Given the following vehicle inspections exist:
      | vehicleinspection                                |
      | {"testid": 10001, "vehicle": {"make": "Toyota"}} |
      | {"testid": 10002, "vehicle": {"make": "Ford"}}   |
    When I send a GET request to "/api/inspections/jsonnative"
    Then the response status code should be 200
    And the "Content-Type" header should be "application/json"
    And the "Transfer-Encoding" header should be "chunked"
    And the response should be a stream of valid JSON objects, each on a new line
