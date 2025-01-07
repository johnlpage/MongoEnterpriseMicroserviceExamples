package com.johnlpage.mews.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class MongoConfig {
    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);
    private final MongoTemplate mongoTemplate;

    public MongoConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // This is important to ensure you are using Native database transactions.
    @Bean
    public MongoTransactionManager transactionManager() {
        logger.info("MongoDB Native Transactions Enabled");
        return new MongoTransactionManager(mongoTemplate.getMongoDatabaseFactory());
    }
}
