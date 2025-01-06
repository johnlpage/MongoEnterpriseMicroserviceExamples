package com.johnlpage.mews.repository;

import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.johnlpage.mews.models.MOTTest;

@Primary
@Repository
public interface MOTTestRepository extends MongoRepository<MOTTest, String>, OptimizedMongoLoadRepository<MOTTest> {
}
