package com.johnlpage.mews.model;

import com.fasterxml.jackson.annotation.*;

import java.util.Date;
import java.util.Map;
import lombok.Data;
import lombok.Singular;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "inspections")
public class VehicleInspection  {

  @Id Long testid;
  Long vehicleid;
  Date testdate;
  String testclass;
  String testtype;
  String testresult;
  Long testmileage;
  String postcode;
  String make;
  String model;
  String colour;
  String files;
  Long capacity;
  Date firstusedate;
  /* Use this to flag from the JSON we want to remove the record */
  @JsonIgnore @Transient @DeleteFlag Boolean deleted;


  /** Use this to capture any fields not captured explicitly
   * As MongoDB's flexibility makes this easy
   * */
  @JsonAnySetter
  @Singular("payload")
  Map<String, Object> payload;


}
