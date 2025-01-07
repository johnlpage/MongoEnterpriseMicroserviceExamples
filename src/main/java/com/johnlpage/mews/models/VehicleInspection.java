package com.johnlpage.mews.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;


import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "inspections")
public class VehicleInspection implements MewsModel {


    @Id
    Long testid;

    Long vehicleid;
    Date testDate;
    String testcless;
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
    Boolean _deleted;

    // Use this to capture any fields not captured explicitly
    @JsonAnyGetter
    @JsonAnySetter
    private Map<String, Object> payload =  new LinkedHashMap<>();

    @Transient
    private Random rng;

    public VehicleInspection() {
        // New ones are false also don't want to find in a query by example.
        this._deleted = false;
    }

    public Boolean get_deleted() {
        return this._deleted;
    }

    public void set_deleted(Boolean _deleted) {
        this._deleted = _deleted;
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

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object>  payload) {
        this.payload = payload;
    }

    public Long getVehicleid() {
        return vehicleid;
    }

    public void setVehicleid(Long vehicleid) {
        this.vehicleid = vehicleid;
    }

    public Date getTestDate() {
        return testDate;
    }

    public void setTestDate(Date testDate) {
        this.testDate = testDate;
    }

    public String getTestcless() {
        return testcless;
    }

    public void setTestcless(String testcless) {
        this.testcless = testcless;
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
    public boolean isDeleted() {
        return _deleted;
    }


    // This code will be very specific to your data and how you want to test
    // May not be required.

    @Override
    public Object getDocumentId() {
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

        // Make specific, realisetic changes
        // Change Mileage in 10%
        if (rng.nextDouble() < 0.1) {
            Integer mileage = (Integer) document.get("testmileage");
            if (mileage != null) {
                mileage = (mileage * 101) / 100;
                document.put("testmileage", mileage);
            }
        }

        //Change result in 10%
        if (rng.nextDouble() < 0.1) {
            String result = (String) document.get("testresult");
            if (result.equalsIgnoreCase("Passed")) {
                document.put("testresult", "Failed");
            } else {
                document.put("testresult", "Passed");
            }
        }

        //Delete 10%
        if (rng.nextDouble() < 0.1) {
            Integer testid = (Integer) document.get("testid");
            document.clear();
            document.put("testid", testid);
            document.put("_deleted", true);
        } else {
            //Make 10% a new test (insert)
            if (rng.nextDouble() < 0.1) {
                Integer testid = (Integer) document.get("testid");
                testid = testid * 100 + rng.nextInt(100); //String concat
                document.put("testid", testid);
            }
        }

    }


}
