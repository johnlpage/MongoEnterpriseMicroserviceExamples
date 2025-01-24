package com.johnlpage.mews.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.repository.VehicleInspectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class VehicleInspectionPreWriteTriggerServiceImpl extends PreWriteTriggerService<VehicleInspection> {

    private Random rng;

    @Autowired
    public VehicleInspectionPreWriteTriggerServiceImpl(
            VehicleInspectionRepository repository, ObjectMapper objectMapper, JsonFactory jsonFactory) {
    }

    /** This code will be very specific to your data and how you want to test it may not be required. */

    /** This Code is for mutable models */

    @Override
    public void  modifyMutableDataPreWrite(VehicleInspection document) {
        final double percentChanged = 1.0;
        // todo: inject this to be singleton
        final Random newRng = this.rng == null ? new Random() : this.rng;

        double skipThis = newRng.nextDouble() * 100.0;

        if (skipThis > percentChanged) {
            return;
        }

        // Make specific, realistic changes
        // Change Mileage in 10%
        Long mileage = document.getTestmileage();

        if (newRng.nextDouble() < 1) {

            if (mileage != null) {
                mileage = (mileage * 101) / 100;
                document.setTestmileage(mileage);
            }
        }

        // Change result in 10%
        if (newRng.nextDouble() < 0.1) {
            String result = (String) document.getTestresult();
            if (result.equalsIgnoreCase("Passed")) {
                document.setTestresult("Failed");
            } else {
                document.setTestresult("Passed");
            }
        }

        // Delete 10%

        if (newRng.nextDouble() < 0.1) {
            document.setDeleted(true);
        } else {
            // Make 10% a new test (insert)
            if (newRng.nextDouble() < 0.1) {
                Long testid = document.getTestid();
                testid = testid * 100 + newRng.nextInt(100); // String concat
                document.setTestid(testid);
            }
        }
    }


    /** This Code is for immutable models */

    /*
    @Override
    public VehicleInspection  newImmutableDataPreWrite(VehicleInspection document) {
        final double percentChanged = 1.0;
        // todo: inject this to be singleton
        final Random newRng = this.rng == null ? new Random() : this.rng;

        double skipThis = newRng.nextDouble() * 100.0;
        if (skipThis > percentChanged) {
            return document;
        }

        // Make specific, realistic changes
        // Change Mileage in 10%
        Long mileage = document.getTestmileage();

        if (newRng.nextDouble() < 0.1) {

            if (mileage != null) {
                mileage = (mileage * 101) / 100;

            }
        }

        String result = (String) document.getTestresult();
        // Change result in 10%
        if (newRng.nextDouble() < 0.1) {

            if (result.equalsIgnoreCase("Passed")) {
               result = "Failed";
            } else {
                result = "Passed";
            }
        }

        Long testid = document.getTestid();
        Boolean deleted = null;

        // Delete 10%
        if (newRng.nextDouble() < 0.1) {
           deleted = true;
        } else {
            // Make 10% a new test (insert)
            if (newRng.nextDouble() < 0.1) {
                testid = testid * 100 + newRng.nextInt(100); // String conca
            }
        }
        return document.toBuilder().testresult(result).testid(testid).deleted(deleted).build();
    }*/

}
