package com.johnlpage.memex.VehicleInspection.repository;

import com.johnlpage.memex.VehicleInspection.model.Vehicle;

import java.util.Optional;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/* The Vehicle Model is not top level, Vehicles are a child of VehicleInspection
   This shows how you can still have a Vehicle repository that works on a child
   element including arrays.
*/
@Repository
public interface VehicleRepository extends MongoRepository<Vehicle, Long> {

    // As we don't have a vehicle collection all Queries need to be sent to VehicleInspection

    // VehicleInspection - Start with matching nothing then use $unionWith to swap collections
    // Find inside the embedded object then use replaceWith to just keep the vehicle object
    // Alternatively we code have projected a top level object matching Vehicle

    @Aggregation(
            pipeline = {
                    "{$match:{_id:null}}",
                    "{ $unionWith: { coll: 'vehicleinspection', pipeline: [ { $match:  { 'vehicle.vehicleid' : ?0 }}] }}",
                    "{$limit: 1}",
                    "{$replaceWith:'$vehicle'}"
            })
    Optional<Vehicle> findByVehicleId(Long id);

    // Fetch a Vehicle then add all it's inspections to it. - N.B make sure this is indexed.
    // This is a pivot compared to the storage format

    @Aggregation(
            pipeline = {
                    "{$match:{_id:null}}",
                    "{ $unionWith: { coll: 'vehicleinspection', pipeline: [ { $match:  { 'vehicle.vehicleid' : ?0 }}] }}",
                    "{$limit: 1}",
                    "{$replaceWith:'$vehicle'}",
                    "{$lookup: { from:  'vehicleinspection', localField: 'vehicleid', foreignField: 'vehicle.vehicleid', "
                            + "as: 'inspections' }}"
            })
    Optional<Vehicle> findByVehicleIdWithInspections(Long id);
}
