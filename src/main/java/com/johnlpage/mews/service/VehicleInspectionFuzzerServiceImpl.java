package com.johnlpage.mews.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.repository.VehicleInspectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class VehicleInspectionFuzzerServiceImpl extends FuzzerService<VehicleInspection> {

    private Random rng;

    @Autowired
    public VehicleInspectionFuzzerServiceImpl(
            VehicleInspectionRepository repository, ObjectMapper objectMapper, JsonFactory jsonFactory) {
    }

    /** This code will be very specific to your data and how you want to test May not be required. */
    @Override
    public void  modifyDataForTest(VehicleInspection document) {
        final double percentChanged = 1.0;
        // todo: inject this to be singleton
        final Random newRng = this.rng == null ? new Random() : this.rng;

        double skipThis = newRng.nextDouble() * 100.0;
        if (skipThis > percentChanged) {
            return;
        }

        // Make specific, realistic changes
        // Change Mileage in 10%
        if (newRng.nextDouble() < 0.1) {
            Long mileage = document.getTestmileage();
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
        return;
    }
}
