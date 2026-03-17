package com.johnlpage.memex.VehicleInspection.repository;

import com.johnlpage.memex.VehicleInspection.model.VehicleInspection;
import com.johnlpage.memex.generics.repository.MongoHistoryRepository;
import com.johnlpage.memex.generics.repository.OptimizedMongoDownstreamRepository;
import com.johnlpage.memex.generics.repository.OptimizedMongoLoadRepository;
import com.johnlpage.memex.generics.repository.OptimizedMongoQueryRepository;

import java.util.List;
import java.util.stream.Stream;

import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

/* The repository Shows examples of the different ways you can define Queries.
   Annotation based (@Quer, @Aggregation), Derived query Methods and
   Custom methods using a variety of types/
*/
@Repository
public interface VehicleInspectionRepository
        extends MongoRepository<VehicleInspection, Long>,
        OptimizedMongoLoadRepository<VehicleInspection>,
        OptimizedMongoQueryRepository<VehicleInspection>,
        OptimizedMongoDownstreamRepository<VehicleInspection>,
        MongoHistoryRepository<VehicleInspection, Long> {


    // Find inspections by engine capacity - Automatically derived query
    List<VehicleInspection> findByCapacityGreaterThan(Long engineCapacity);

    // Derived Query with Boolean and
    List<VehicleInspection> findByVehicleColourAndVehicleModel(String colour, String model);

    // Version that gets a page using Slice as Page is a lot slower due to counting
    Slice<VehicleInspection> findByVehicleModel(String model, PageRequest page);

    // Annotation-based aggregation (Group By in this case) returning a Docuement as a generic type
    @Aggregation(
            pipeline = {
                    "{ '$match': { 'vehicle.model': ?0 } }",
                    "{ '$group': { '_id': null, 'averageMileage': { '$avg': '$vehicle.mileage' } } }"
            })
    List<Document> findAverageMileageByModel(String model);


    // Simple efficient annotation update without reading or sending the whole document (N.B won't do history)
    @Query("{ 'testid' : ?0 }")
    @Update("{ 'inc' : { 'testmileage' : ?1 } }")
    void adjustTestMileage(Long testid, int increment);

    // An Annotation using MongoDB Query Language - opens up more query operators
    @Query("{ 'vehicle.make': ?0, 'vehicle.model': ?1 }")
    List<VehicleInspection> findByVehicleMakeAndModel(String make, String model);


    // Fetch whole collection as a stream.
    Stream<VehicleInspection> findAllBy();


}
