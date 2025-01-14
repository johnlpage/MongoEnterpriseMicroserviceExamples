package com.johnlpage.mews.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

@Value
@Jacksonized
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "inspections")
public class VehicleInspection implements MewsModel<Long> {

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
  @JsonIgnore @Transient boolean toDelete;

  /** Use this to capture any fields not captured explicitly */
  @JsonAnyGetter
  @JsonAnySetter
  @Singular("payload")
  Map<String, Object> payload;

  @JsonIgnore @Transient Random rng;

  @Override
  @JsonIgnore
  public boolean toDelete() {
    return toDelete;
  }

  @Override
  @JsonIgnore
  public Long getDocumentId() {
    return testid;
  }

  /** This code will be very specific to your data and how you want to test May not be required. */
  @Override
  public Map<String, Object> modifyDataForTest(Map<String, Object> document) {
    final double percentChanged = 1.0;
    // todo: inject this to be singleton
    final Random newRng = this.rng == null ? new Random() : this.rng;

    double skipThis = newRng.nextDouble() * 100.0;
    if (skipThis > percentChanged) {
      return document;
    }

    // Make specific, realistic changes
    // Change Mileage in 10%
    if (newRng.nextDouble() < 0.1) {
      Integer mileage = (Integer) document.get("testmileage");
      if (mileage != null) {
        mileage = (mileage * 101) / 100;
        document.put("testmileage", mileage);
      }
    }

    // Change result in 10%
    if (newRng.nextDouble() < 0.1) {
      String result = (String) document.get("testresult");
      if (result.equalsIgnoreCase("Passed")) {
        document.put("testresult", "Failed");
      } else {
        document.put("testresult", "Passed");
      }
    }

    // Delete 10%
    if (newRng.nextDouble() < 0.1) {
      Integer testid = (Integer) document.get("testid");
      document.clear();
      document.put("testid", testid);
      document.put("_deleted", true);
    } else {
      // Make 10% a new test (insert)
      if (newRng.nextDouble() < 0.1) {
        Integer testid = (Integer) document.get("testid");
        testid = testid * 100 + newRng.nextInt(100); // String concat
        document.put("testid", testid);
      }
    }
    return document;
  }
}
