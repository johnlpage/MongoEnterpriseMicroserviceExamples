package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashFunction;
import com.google.common.hash.HashCode;

/**
 * This class is used to generate values rather than use explicit ones
 *
 */

// TODO - Integrate with Faker so you can use @ for anythign from Faker

public class ValueMaker {
        Random rng;
        Long oneup = 0L;
        String directoryPath;
        Map<String,DataGenProcessor> processors;
        HashFunction murmurHash3 = Hashing.murmur3_128();

        Map<HashCode,ObjectNode> jsonCache;

    ValueMaker(Random rng, String directoryPath) {
            this.rng = rng;
            this.directoryPath = directoryPath;
            processors = new HashMap<>();
            jsonCache = new HashMap<>();
        }

        Object expandValue(String input) throws IOException {
            if(input.startsWith("@") == false) {
                return input;
            }
            if(input.equals("@ONEUP"))
            {
                oneup++;
                return oneup;
            }

            if(input.startsWith("@INTEGER("))
            {
                input = input.substring(input.indexOf("(") + 1, input.lastIndexOf(")"));
                String[] tofrom = input.split(",");
                int from = Integer.parseInt(tofrom[0]);
                int to = Integer.parseInt(tofrom[1]);
                int chosen = rng.nextInt(to-from+1)+from;
                return chosen;
            }

            if(input.startsWith("@DOUBLE("))
            {
                input = input.substring(input.indexOf("(") + 1, input.lastIndexOf(")"));
                String[] tofrom = input.split(",");
               double to = Double.parseDouble(tofrom[0]);
                double from = Double.parseDouble(tofrom[1]);
                double chosen = (rng.nextDouble()*(to-from))+from;
            }

            if(input.startsWith("@DATE("))
            {
                input = input.substring(input.indexOf("(") + 1, input.lastIndexOf(")"));
                String[] tofrom = input.split(",");

                    LocalDate  startDate = LocalDate.parse(tofrom[0]);
                LocalDate endDate = LocalDate.parse(tofrom[1]);
                LocalDate chosenDate = getRandomDateBetween(startDate, endDate);

                return chosenDate;

            }

            if(input.startsWith("@DATETIME("))
            {
                input = input.substring(input.indexOf("(") + 1, input.lastIndexOf(")"));
                String[] tofrom = input.split(",");

                LocalDateTime  startDateTime = LocalDateTime.parse(tofrom[0]);
                LocalDateTime endDateTime = LocalDateTime.parse(tofrom[1]);
                LocalDateTime chosenDate = getRandomDateTimeBetween(startDateTime, endDateTime);

                return chosenDate;

            }

            if(input.startsWith("@JSON(")) {
                input = input.substring(input.indexOf("(") + 1, input.lastIndexOf(")"));
                HashCode hc = murmurHash3.hashString(input, StandardCharsets.UTF_8);
                ObjectNode json;
                json = jsonCache.get(hc);
                if(json == null) {
                    json = (ObjectNode) new ObjectMapper().readTree(input);
                    jsonCache.put(hc, json);
                }
                return json;
            }

            if(input.startsWith("@ARRAY(")) {
                input = input.substring(input.indexOf("(") + 1, input.lastIndexOf(")"));
                String[] parts = input.split(",");
                int n = Integer.parseInt(parts[1]);
                // Cache the Processors
                DataGenProcessor subProcessor = processors.get(parts[0]);
                if(subProcessor == null) {
                    subProcessor = new DataGenProcessor(directoryPath + "/" + parts[0]);
                    processors.put(parts[0], subProcessor);
                }

                List<ObjectNode> rval = subProcessor.generateJsonDocuments(n);
                return rval;
            }

            return input; // Didn't know what to do with it
        }


    private LocalDate getRandomDateBetween(LocalDate startDate, LocalDate endDate) {
        // Calculate the number of days between startDate and endDate
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        // Generate a random number of days to add to the startDate
        long randomDays = rng.nextInt((int) daysBetween+1);
        // Return the result of adding the random number of days to startDate
        return startDate.plusDays(randomDays);
    }

    private LocalDateTime getRandomDateTimeBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        // Calculate the number of days between startDate and endDate
        long minutesBetween = ChronoUnit.DAYS.between(startDateTime, endDateTime);
        // Generate a random number of days to add to the startDate
        long randomMinutes = rng.nextInt((int) minutesBetween+1);
        // Return the result of adding the random number of days to startDate
        return startDateTime.plusMinutes(randomMinutes);
    }
}

