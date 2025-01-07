package com.johnlpage.mews.repository;

import com.johnlpage.mews.models.VehicleInspection;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Primary
@Repository
public interface VehicleInspectionRepository extends MongoRepository<VehicleInspection, Long>, OptimizedMongoLoadRepository<VehicleInspection> {
}
