@restapi @vehicle_inspection
Feature: Vehicle Inspection REST API - Point-in-Time History (As Of)
  This feature covers the REST API functionality for retrieving the state of vehicle inspections as they were at a specific point in time (as-of queries).
  It verifies that historical data can be accurately fetched based on a given timestamp.

  @get @as_of @sunny_day
  Scenario: Successfully retrieve vehicle inspection history as of a specific date
    Given the following vehicle inspections exist:
      | vehicleinspection                                                |
      | {"testid": 10001, "vehicle": {"make": "Ford", "model": "Focus"}} |
    And I wait for 1 second
    And I capture the current timestamp to "<timestamp>" with "yyyyMMddHHmmss" pattern
    And I wait for 1 second
    And I send a POST request to "/api/inspections?updateStrategy=UPDATEWITHHISTORY&futz=true" with the payload:
      """
      [
        {
          "testid": 10001,
          "testdate": "2025-10-27T11:00:00Z",
          "testclass": "Class 2",
          "testtype": "Interim",
          "testresult": "PASS",
          "testmileage": 60000,
          "postcode": "SW1A 0AB",
          "fuel": "Diesel",
          "capacity": 80,
          "firstusedate": "2019-03-20T00:00:00Z",
          "faileditems": ["Brakes", "Lights"],
          "vehicle": {
            "make": "Ford",
            "model": "Fiesta",
            "year": 2019,
            "vin": "VIN0987654321FEDCBA"
          }
        }
      ]
      """
    And the response status code should be 200
    When I send a GET request to "/api/inspections/asOf?id=10001&asOfDate=<timestamp>"
    Then the response status code should be 200
    And the "Transfer-Encoding" header should be "chunked"
    And the "Content-Type" header should be "application/json"
    And the response should contain "testid": 10001
    And the response should contain "combined.vehicle.model": "Focus"
