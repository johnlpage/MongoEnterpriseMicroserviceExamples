package com.johnlpage.mews.controllers;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.johnlpage.mews.models.VehicleInspection;
import com.johnlpage.mews.repository.VehicleInspectionRepository;
import com.johnlpage.mews.service.MongoDBJSONLoaderService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/vosa")
public class VehicleInspectionLoadController {

    private static final Logger logger = LoggerFactory.getLogger(VehicleInspectionLoadController.class);

    @Autowired
    private MongoDBJSONLoaderService<VehicleInspectionRepository, VehicleInspection> motTestLoader;


    /*
     * This could be something that reads a file, or even from a Kafka Queue as long
     * as it gets a stream of JSON data - using an HTTP endpoint to demonstrate.
     */

    @PostMapping("/mot")
    public void loadFromStream(HttpServletRequest request) {
        motTestLoader.useUpdateNotReplace(true);

        try {
            motTestLoader.loadFromJSONStream(request.getInputStream(), VehicleInspection.class, false);
        } catch (IOException e) {
           logger.error(e.getMessage());
        }
    }

    //Difference is this tells the loader to futz with the data for testing purposes - not for 
    //Production use.

    @PostMapping("/motfutz")
    public void loadFuzzedFromStream(HttpServletRequest request) {
        motTestLoader.useUpdateNotReplace(true);
        try {
            motTestLoader.loadFromJSONStream(request.getInputStream(),VehicleInspection.class, true);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

  

}
