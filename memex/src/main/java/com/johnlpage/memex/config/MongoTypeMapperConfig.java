package com.johnlpage.memex.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

@Configuration
public class MongoTypeMapperConfig {

    private final MappingMongoConverter mappingMongoConverter;

    @Autowired
    public MongoTypeMapperConfig(MappingMongoConverter mappingMongoConverter) {
        this.mappingMongoConverter = mappingMongoConverter;
    }

    @PostConstruct
    public void removeClassField() {
        // Disabling the _class field as it has a significant impact on query performance
        // when included in queries but not in the index.
        mappingMongoConverter.setTypeMapper(new DefaultMongoTypeMapper(null));
    }
}
