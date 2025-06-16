@restapi @atlas_search @vehicle_inspection
Feature: Vehicle Inspection REST API - Atlas Search Functionality
  This feature tests the integration of the REST API with Atlas Search for vehicle inspections.
  It ensures that search queries can be successfully executed against the inspection data, returning relevant results and search scores.

  @post @sunny_day
  Scenario: Successfully execute an Atlas Search query
    Given the following vehicle inspections exist:
      | vehicleinspection                                  |
      | {"testid": 10001, "vehicle": {"model": "Corolla"}} |
    And I wait for 1 second
    When I send a POST request to "/api/inspections/search" with the payload:
      """
      {
        "search": {
          "text": {
            "query": "Corolla",
            "path": "vehicle.model"
          }
        },
         "projection": {
           "_id": 1,
           "vehicle": 1,
           "score": { "$meta": "searchScore" }
         }
      }
      """
    Then the response status code should be 200
    And the response should be a non empty JSON array
    And each item in the response array should contain "vehicle.model": "Corolla"

#  Uncomment once the code is fixed
#  @post @rainy_day
#  Scenario: Fail to execute an Atlas Search query due to invalid syntax
#    When I send a POST request to "/api/inspections/search" with the payload:
#      """
#      { "search": { "invalid_field": "value" } }
#      """
#    Then the response status code should be 500
