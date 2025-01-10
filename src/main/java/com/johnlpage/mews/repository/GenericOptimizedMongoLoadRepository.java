package com.johnlpage.mews.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GenericOptimizedMongoLoadRepository<T, ID>
    extends MongoRepository<T, ID>, OptimizedMongoLoadRepository<T> {}
