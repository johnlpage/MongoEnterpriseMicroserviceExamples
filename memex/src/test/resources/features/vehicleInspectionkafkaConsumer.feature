Feature: Vehicle Inspection Kafka consumer

  Scenario: Vehicle Inspection Kafka consumer listens to sent messages
    Given 100 vehicle inspections records are sent to kafka with capacity 60
    And I wait for 2 second
    Then verify 100 records are saved in mongo with capacity 60