Feature: Vehicle Inspections API

  Scenario: Get Inspection by ID
    When the user sends GET request to "/api/inspections/id/1"
    Then the response should have status code 200
    Then the response should contain the Vehicle Inspection with id 1