@kafka @vehicle_inspection
Feature: Vehicle Inspection Kafka Integration
  This feature tests the integration of vehicle inspections with Kafka.
  It ensures that messages sent to Kafka topics are correctly consumed and processed,
  leading to the appropriate storage or update of inspection data in the system.

  @kafka @sunny_day
  Scenario: Vehicle Inspection Kafka consumer listens to sent messages
    Given the vehicle inspections in range 10000-11000 do not exist
    When I send 100 vehicle inspections starting with id 10000 to kafka "test" topic with:
    """
    {"capacity": 60, "vehicle": {"make": "Ford"}}
    """
    Then I wait for 2 seconds
    And 100 vehicle inspections starting from id 10000 do exist with:
    """
    {"capacity": 60, "vehicle": {"make": "Ford"}}
    """
