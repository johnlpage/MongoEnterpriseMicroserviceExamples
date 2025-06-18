package com.johnlpage.memex.repository;

import com.johnlpage.memex.model.Vehicle;
import java.util.Optional;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/* The repository lets you define simple operations by name of function or by Javascript
versions of MongoDB queries
*/
@Repository
public interface VehicleRepository extends MongoRepository<Vehicle, Long> {

  // We don't have a separate vehicle collection so any Queries need to be sent to VehicleInspection

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
