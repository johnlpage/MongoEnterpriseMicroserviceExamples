package com.johnlpage.memex.cucumber;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;


@TestConfiguration
public class CucumberTestsContainersConfig {

    @Bean
    @Conditional(MongoUriMissingCondition.class)
    public MongoDBAtlasLocalContainer mongoDbContainer() {
        return new MongoDBAtlasLocalContainer("mongodb/mongodb-atlas-local:8");
    }

    @Bean
    @Conditional(MongoUriMissingCondition.class)
    public DynamicPropertyRegistrar mongoDbProperties(MongoDBAtlasLocalContainer mongoDBContainer) {
        mongoDBContainer.start();
        return (registry) -> {
            registry.add("spring.data.mongodb.uri", mongoDBContainer::getConnectionString);
            registry.add("spring.data.mongodb.database", () -> "memex");
        };
    }

    static class MongoUriMissingCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
            String uri = context.getEnvironment().getProperty("spring.data.mongodb.uri");
            return uri == null || uri.trim().isEmpty();
        }
    }
}
