package com.johnlpage.memex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.ReadOnlyProperty;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Vehicle {
  Long vehicleid;
  String make;
  String model;
  String colour;

  // This is never saved to the DB - but we can populate it by JOIN ($lookup) on read
  // We can do this in a function (possible easier to understand) or an annotation

  @ReadOnlyProperty List<VehicleInspection> inspections;
}
