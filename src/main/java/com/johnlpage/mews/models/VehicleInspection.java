package com.johnlpage.mews.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "inspections")
public class VehicleInspection implements MewsModel<Long> {

  @Id private Long testid;

  private Long vehicleid;
  private Date testdate;
  private String testclass;
  private String testtype;
  private String testresult;
  private Long testmileage;
  private String postcode;
  private String make;
  private String model;
  private String colour;
  private String files;
  private Long capacity;
  private Date firstusedate;
  @Transient private Boolean toDelete = false;

  /** Use this to capture any fields not captured explicitly */
  @JsonAnyGetter @JsonAnySetter private Map<String, Object> payload = new LinkedHashMap<>();

  @Transient private Random rng;

  public VehicleInspection() {}

  public Date getTestdate() {
    return testdate;
  }

  public void setTestdate(Date testdate) {
    this.testdate = testdate;
  }

  @JsonIgnore
  public Boolean getToDelete() {
    return toDelete;
  }

  public void setToDelete(Boolean deleted) {
    this.toDelete = deleted;
  }

  public String getTestresult() {
    return testresult;
  }

  public void setTestresult(String testresult) {
    this.testresult = testresult;
  }

  public Long getTestid() {
    return testid;
  }

  public void setTestid(Long testid) {
    this.testid = testid;
  }

  public Long getVehicleid() {
    return vehicleid;
  }

  public void setVehicleid(Long vehicleid) {
    this.vehicleid = vehicleid;
  }

  public String getTesttype() {
    return testtype;
  }

  public void setTesttype(String testtype) {
    this.testtype = testtype;
  }

  public Long getTestmileage() {
    return testmileage;
  }

  public void setTestmileage(Long testmileage) {
    this.testmileage = testmileage;
  }

  public String getPostcode() {
    return postcode;
  }

  public void setPostcode(String postcode) {
    this.postcode = postcode;
  }

  public String getMake() {
    return make;
  }

  public void setMake(String make) {
    this.make = make;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getColour() {
    return colour;
  }

  public void setColour(String colour) {
    this.colour = colour;
  }

  public String getFiles() {
    return files;
  }

  public void setFiles(String files) {
    this.files = files;
  }

  public Long getCapacity() {
    return capacity;
  }

  public void setCapacity(Long capacity) {
    this.capacity = capacity;
  }

  public Date getFirstusedate() {
    return firstusedate;
  }

  public void setFirstusedate(Date firstusedate) {
    this.firstusedate = firstusedate;
  }

  @Override
  public boolean toDelete() {
    return toDelete;
  }

  // This code will be very specific to your data and how you want to test
  // May not be required.

  @Override
  @JsonIgnore
  public Long getDocumentId() {
    return testid;
  }

  @Override
  public void modifyDataForTest(Map<String, Object> document) {
    final double percentChanged = 1.0;
    if (this.rng == null) {
      this.rng = new Random();
    }

    double skipThis = rng.nextDouble() * 100.0;
    if (skipThis > percentChanged) {
      return;
    }

    // Make specific, realistic changes
    // Change Mileage in 10%
    if (rng.nextDouble() < 0.1) {
      Integer mileage = (Integer) document.get("testmileage");
      if (mileage != null) {
        mileage = (mileage * 101) / 100;
        document.put("testmileage", mileage);
      }
    }

    // Change result in 10%
    if (rng.nextDouble() < 0.1) {
      String result = (String) document.get("testresult");
      if (result.equalsIgnoreCase("Passed")) {
        document.put("testresult", "Failed");
      } else {
        document.put("testresult", "Passed");
      }
    }

    // Delete 10%
    if (rng.nextDouble() < 0.1) {
      Integer testid = (Integer) document.get("testid");
      document.clear();
      document.put("testid", testid);
      document.put("_deleted", true);
    } else {
      // Make 10% a new test (insert)
      if (rng.nextDouble() < 0.1) {
        Integer testid = (Integer) document.get("testid");
        testid = testid * 100 + rng.nextInt(100); // String concat
        document.put("testid", testid);
      }
    }
  }
}
