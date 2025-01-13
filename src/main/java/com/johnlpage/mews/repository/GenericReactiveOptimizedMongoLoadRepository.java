package com.johnlpage.mews.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GenericReactiveOptimizedMongoLoadRepository<T, ID>
    extends ReactiveMongoRepository<T, ID>, ReactiveOptimizedMongoLoadRepository<T> {}
