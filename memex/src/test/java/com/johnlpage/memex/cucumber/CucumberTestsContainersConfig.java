package com.johnlpage.memex.cucumber;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

@TestConfiguration
public class CucumberTestsContainersConfig {

    @Bean
    @ConditionalOnProperty(name = "memex.testcontainers.mongodb.enabled", havingValue = "true", matchIfMissing = true)
    public MongoDBAtlasLocalContainer mongoDbContainer() {
        return new MongoDBAtlasLocalContainer("mongodb/mongodb-atlas-local:8");
    }

    @Bean
    @ConditionalOnProperty(name = "memex.testcontainers.mongodb.enabled", havingValue = "true", matchIfMissing = true)
    public DynamicPropertyRegistrar mongoDbProperties(MongoDBAtlasLocalContainer mongoDBContainer) {
        mongoDBContainer.start();
        return (registry) -> {
            registry.add("spring.data.mongodb.uri", mongoDBContainer::getConnectionString);
            registry.add("spring.data.mongodb.database", () -> "memex");
        };
    }
}
