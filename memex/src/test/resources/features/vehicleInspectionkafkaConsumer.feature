Feature: Vehicle Inspection Kafka consumer

  Scenario: Vehicle Inspection Kafka consumer listens to sent messages
    Given the vehicle inspections in range 10000-11000 do not exist
    When I send 100 vehicle inspections starting with id 10000 to kafka with:
    """
    {"capacity": 60, "vehicle": {"make": "Ford"}}
    """
    Then I wait for 2 second
    And verify 100 vehicle inspections are saved starting from id 10000 in mongo with:
    """
    {"capacity": 60, "vehicle": {"make": "Ford"}}
    """

