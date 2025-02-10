package com.johnlpage.mews.repository;

import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.repository.optimized.OptimizedMongoDownstreamRepository;
import com.johnlpage.mews.repository.optimized.OptimizedMongoLoadRepository;
import com.johnlpage.mews.repository.optimized.OptimizedMongoQueryRepository;
import java.util.List;
import java.util.stream.Stream;
import org.bson.Document;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

/* The repository lets you define simple opertions by name of function or by Javascript
versions of MongoDB queries
*/
@Repository
public interface VehicleInspectionRepository
    extends MongoRepository<VehicleInspection, Long>,
        OptimizedMongoLoadRepository<VehicleInspection>,
        OptimizedMongoQueryRepository<VehicleInspection>,
        OptimizedMongoDownstreamRepository<VehicleInspection> {

  // Find inspections by engine capacity - auto generated query
  List<VehicleInspection> findByCapacityGreaterThan(Long engineCapacity);

  // Custom query to find vehicle inspections by vehicle make and model
  @Query("{ 'vehicle.make': ?0, 'vehicle.model': ?1 }")
  List<VehicleInspection> findByVehicleMakeAndModel(String make, String model);

  // Custom aggregation to compute mean mileage of a given model
  @Aggregation(
      pipeline = {
        "{ '$match': { 'vehicle.model': ?0 } }",
        "{ '$group': { '_id': null, 'averageMileage': { '$avg': '$vehicle.mileage' } } }"
      })
  List<Document> findAverageMileageByModel(String model);

  // Simple efficient update without reading or sending whole document (N.B won't do history)
  @Query("{ 'testid' : ?0 }")
  @Update("{ 'inc' : { 'testmileage' : ?1 } }")
  void adjustTestMileage(Long testid, int increment);

  // You have to explicitly call out a streaming version here if you want it.
  Stream<VehicleInspection> findAllBy();
}
