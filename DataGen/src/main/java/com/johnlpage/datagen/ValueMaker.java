package com.johnlpage.datagen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * This class is used to generate values rather than use explicit ones
 */


public class ValueMaker {
    Random rng;
    Long oneup = 0L;
    String directoryPath;
    Map<String, DataGenProcessor> processors;
    HashFunction murmurHash3 = Hashing.murmur3_128();

    Map<HashCode, ObjectNode> jsonCache;

    ValueMaker(Random rng, String directoryPath) {
        this.rng = rng;
        this.directoryPath = directoryPath;
        processors = new HashMap<>();
        jsonCache = new HashMap<>();
    }

    Object expandValue(String input) throws IOException {
        if (!input.startsWith("@")) {
            return input;
        }
        if (input.equals("@ONEUP")) {
            oneup++;
            return oneup;
        }

        String argString = input.substring(input.indexOf("(") + 1, input.lastIndexOf(")"));
        String[] args = argString.split(",");

        if (input.startsWith("@INTEGER(")) {
            int from = Integer.parseInt(args[0]);
            int to = Integer.parseInt(args[1]);
            return rng.nextInt(to - from + 1) + from;
        }

        if (input.startsWith("@DOUBLE(")) {
            double to = Double.parseDouble(args[0]);
            double from = Double.parseDouble(args[1]);
            return (rng.nextDouble() * (to - from)) + from;
        }

        if (input.startsWith("@DATE(")) {
            LocalDate startDate = LocalDate.parse(args[0]);
            LocalDate endDate = LocalDate.parse(args[1]);
            return getRandomDateBetween(startDate, endDate);
        }

        if (input.startsWith("@DATETIME(")) {
            LocalDateTime startDateTime = LocalDateTime.parse(args[0]);
            LocalDateTime endDateTime = LocalDateTime.parse(args[1]);
            return getRandomDateTimeBetween(startDateTime, endDateTime);
        }

        if (input.startsWith("@JSON(")) {

            //JSON parsing is expensive so cache the results in a Map against a hash of the input
            HashCode hc = murmurHash3.hashString(argString, StandardCharsets.UTF_8);
            ObjectNode json = jsonCache.get(hc);
            if (json == null) {
                json = (ObjectNode) new ObjectMapper().readTree(argString);
                jsonCache.put(hc, json);
            }
            return json;
        }

        if (input.startsWith("@ARRAY(")) {
            String[] parts = argString.split(",");
            int n = Integer.parseInt(parts[1]);
            // Cache the Processors
            DataGenProcessor subProcessor = processors.get(parts[0]);
            if (subProcessor == null) {
                subProcessor = new DataGenProcessor(directoryPath + "/" + parts[0]);
                processors.put(parts[0], subProcessor);
            }

            return subProcessor.generateJsonDocuments(n);
        }

        return input; // Didn't know what to do with it
    }


    private LocalDate getRandomDateBetween(LocalDate startDate, LocalDate endDate) {
        // Calculate the number of days between startDate and endDate
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        // Generate a random number of days to add to the startDate
        long randomDays = rng.nextInt((int) daysBetween + 1);
        // Return the result of adding the random number of days to startDate
        return startDate.plusDays(randomDays);
    }

    private LocalDateTime getRandomDateTimeBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        // Calculate the number of days between startDate and endDate
        long minutesBetween = ChronoUnit.DAYS.between(startDateTime, endDateTime);
        // Generate a random number of days to add to the startDate
        long randomMinutes = rng.nextInt((int) minutesBetween + 1);
        // Return the result of adding the random number of days to startDate
        return startDateTime.plusMinutes(randomMinutes);
    }
}

