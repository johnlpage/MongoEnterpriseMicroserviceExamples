package com.johnlpage.mews.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class MongoConfig implements InitializingBean {
  private static final Logger LOG = LoggerFactory.getLogger(MongoConfig.class);
  private final MongoTemplate mongoTemplate;
  @Autowired @Lazy private MappingMongoConverter mappingMongoConverter;

  @Autowired
  public MongoConfig(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /** This is important to ensure you are using Native database transactions. */
  @Bean
  public MongoTransactionManager transactionManager() {
    LOG.info("MongoDB Native Transactions Enabled");
    return new MongoTransactionManager(mongoTemplate.getMongoDatabaseFactory());
  }

  @Override
  public void afterPropertiesSet() {
    // We are disabling the _class field here as it has a significant impact on query performance
    // When it gets included in queries but is not in the index. You only need it when you have
    // polymorphism for a collection.
    mappingMongoConverter.setTypeMapper(new DefaultMongoTypeMapper(null));
  }
}
