@restapi @mongo_query @vehicle_inspection
Feature: Vehicle Inspection REST API - Native MongoDB Query Execution
  This feature tests the capability of the REST API to execute native MongoDB queries directly against the vehicle inspection collection.
  It verifies that complex queries involving filters and sorting can be performed successfully.

  @post @sunny_day
  Scenario: Successfully execute a native MongoDB query with sorting and filter on non-indexed field
    Given the following vehicle inspections exist:
      | vehicleinspection                                |
      | {"testid": 10001, "vehicle": {"make": "Toyota"}} |
      | {"testid": 10002, "vehicle": {"make": "Ford"}}   |
    When I send a POST request to "/api/inspections/query" with the payload:
      """
      {
        "filter": {
          "vehicle.make": "Toyota"
         },
         "sort": {
           "testdate": 1
         }
      }
      """
    Then the response status code should be 200
    And the response should be a non empty JSON array
    And each item in the response array should contain "vehicle.make": "Toyota"

  @post @sunny_day
  Scenario: Successfully execute a native MongoDB query with sorting and filter on indexed field
    Given the following vehicle inspections exist:
      | vehicleinspection                                  |
      | {"testid": 10001, "vehicle": {"model": "Corolla"}} |
      | {"testid": 10002, "vehicle": {"model": "Focus"}}   |
    When I send a POST request to "/api/inspections/query" with the payload:
      """
      {
        "filter": {
          "vehicle.model": "Corolla"
         },
         "sort": {
           "testdate": 1
         }
      }
      """
    Then the response status code should be 200
    And the response should be a non empty JSON array
    And each item in the response array should contain "vehicle.model": "Corolla"

  @post @rainy_day
  Scenario: Fail to execute a native MongoDB query due to invalid syntax
    When I send a POST request to "/api/inspections/query" with the payload:
      """{
        "filter": {
          "$invalid_operator": "value"
        }
      }
      """
    Then the response status code should be 500
    And the response should contain "Internal Server Error"
