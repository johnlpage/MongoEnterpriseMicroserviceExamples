package com.johnlpage.memex.VehicleInspection.service;

import com.johnlpage.memex.VehicleInspection.model.VehicleInspection;
import com.johnlpage.memex.VehicleInspection.repository.VehicleInspectionRepository;
import com.johnlpage.memex.generics.service.PreWriteTriggerService;

import java.util.Random;

import org.springframework.stereotype.Service;

@Service
public class VehicleInspectionPreWriteTriggerService
        extends PreWriteTriggerService<VehicleInspection> {

    private final Random rng;

    public VehicleInspectionPreWriteTriggerService(VehicleInspectionRepository repository) {
        this.rng = new Random();
    }

  /*
   This code will be very specific to your data and how you want to test it may not be required.
  */

    /**
     * This Code is for mutable models
     */
    @Override
    public void modifyMutableDataPreWrite(VehicleInspection document) {
        final double percentChanged = 1.0;
        double skipThis = rng.nextDouble() * 100.0;
        if (skipThis > percentChanged) {
            return;
        }

        // Make specific, realistic changes Change Mileage in 10%
        Long mileage = document.getTestmileage();
        if (rng.nextDouble() < 0.1) {
            if (mileage != null) {
                mileage = (mileage * 101) / 100;
                document.setTestmileage(mileage);
            }
        }

        // Change result in 10%
        if (rng.nextDouble() < 0.1) {
            String result = document.getTestresult();
            if (result.equalsIgnoreCase("Passed")) {
                document.setTestresult("Failed");
            } else {
                document.setTestresult("Passed");
            }
        }

        // Delete 10%

        if (rng.nextDouble() < 0.1) {
            document.setDeleted(true);
        } else {
            // Make 10% a new test (insert)
            if (rng.nextDouble() < 0.1) {
                Long testid = document.getTestid();
                testid = testid * 100 + rng.nextInt(100); // String concat
                document.setTestid(testid);
            }
        }
    }

    /* This Code is used for immutable models */

  /*
  @Override
  public VehicleInspection  newImmutableDataPreWrite(VehicleInspection document) {
      final double percentChanged = 1.0;
      if (this.rng == null) {
          this.rng = new Random();
      }

      double skipThis = rng.nextDouble() * 100.0;
      if (skipThis > percentChanged) {
          return document;
      }

      // Make specific, realistic changes
      // Change Mileage in 10%
      Long mileage = document.getTestmileage();

      if (rng.nextDouble() < 0.1) {

          if (mileage != null) {
              mileage = (mileage * 101) / 100;

          }
      }

      String result = (String) document.getTestresult();
      // Change result in 10%
      if (rng.nextDouble() < 0.1) {

          if (result.equalsIgnoreCase("Passed")) {
             result = "Failed";
          } else {
              result = "Passed";
          }
      }

      Long testid = document.getTestid();
      Boolean deleted = null;

      // Delete 10%
      if (rng.nextDouble() < 0.1) {
         deleted = true;
      } else {
          // Make 10% a new test (insert)
          if (rng.nextDouble() < 0.1) {
              testid = testid * 100 + rng.nextInt(100);
          }
      }
      return document.toBuilder().testresult(result).testid(testid).deleted(deleted).build();
  }*/

}
