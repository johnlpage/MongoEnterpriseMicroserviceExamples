package com.johnlpage.mews.controllers;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.johnlpage.mews.models.MOTTest;
import com.johnlpage.mews.repository.MOTTestRepository;
import com.johnlpage.mews.service.MongoDBJSONLoaderService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/vosa")
public class MOTTestLoadController {

    private static final Logger logger = LoggerFactory.getLogger(MOTTestLoadController.class);

    @Autowired
    private MongoDBJSONLoaderService<MOTTestRepository, MOTTest> motTestLoader;


    /*
     * This could be something that reads a file, or even from a Kafka Queue as long
     * as it gets a stream of JSON data - using an HTTP endpoint to demonstrate.
     */

    @PostMapping("/mot")
    public void loadFromStream(HttpServletRequest request) {
        motTestLoader.useUpdateNotReplace(true);

        try {
            motTestLoader.loadFromJSONStream(request.getInputStream(), MOTTest.class, false);
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
            motTestLoader.loadFromJSONStream(request.getInputStream(),MOTTest.class, true);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

  

}
