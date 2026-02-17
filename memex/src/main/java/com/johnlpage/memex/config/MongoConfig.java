package com.johnlpage.memex.config;

import com.johnlpage.memex.util.MongoVersionBean;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
public class MongoConfig {
    private static final Logger LOG = LoggerFactory.getLogger(MongoConfig.class);

    private final MongoDatabaseFactory mongoDatabaseFactory;

    @Autowired
    public MongoConfig(MongoDatabaseFactory mongoDatabaseFactory) {
        this.mongoDatabaseFactory = mongoDatabaseFactory;
    }

    @Bean
    public MongoCustomConversions customConversions() {
        return MongoCustomConversions.create(adapter ->
                adapter.useNativeDriverJavaTimeCodecs()
        );
    }

    /**
     * This is important to ensure you are using Native database transactions.
     */
    @Bean
    public MongoTransactionManager transactionManager() {
        LOG.info("MongoDB Native Transactions Enabled");
        return new MongoTransactionManager(mongoDatabaseFactory);
    }

    @Bean
    public MongoVersionBean mongoVersionBean(MongoClient mongoClient) {
        Document buildInfo = mongoClient.getDatabase("admin").runCommand(new Document("buildInfo", 1));
        String version = buildInfo.getString("version");
        return new MongoVersionBean(version);
    }
}
