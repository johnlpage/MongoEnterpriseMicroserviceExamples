package com.johnlpage.mews.repository;

import com.johnlpage.mews.model.VehicleInspection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleInspectionRepository
    extends MongoRepository<VehicleInspection, Long>,
        OptimizedMongoLoadRepository<VehicleInspection> {}
