package com.johnlpage.datagen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

/** This class is used to generate values rather than use explicit ones */
public class ValueMaker {
  Random rng;
  Long oneup = 0L;
  // Kept separately from `oneup` (which advances as @ONEUP is evaluated) so that when a
  // new @ARRAY sub-generator/ValueMaker is lazily created (see @ARRAY handling below), it
  // can be seeded with the *original* oneupStart passed in on the command line, not
  // whatever value this generator's own @ONEUP counter has already advanced to. This is
  // what makes `oneupStart` actually protect against @ONEUP collisions across multiple
  // parallel instances even for @ONEUP fields nested inside @ARRAY sub-directories -
  // every generator/sub-generator in a given run/process starts counting from the same
  // process-wide oneupStart (each still keeps its own independent counter from there, per
  // CSV-file/@ARRAY site - see README).
  long oneupStart;
  String directoryPath;
  Map<String, DataGenProcessor> processors;

  Map<Long, ObjectNode> jsonCache;

  ValueMaker(Random rng, String directoryPath) {
    this(rng, directoryPath, 0L);
  }

  ValueMaker(Random rng, String directoryPath, long oneupStart) {
    this.rng = rng;
    this.directoryPath = directoryPath;
    this.oneup = oneupStart;
    this.oneupStart = oneupStart;
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

    String[] args;
    String argString;
    try {
      argString = input.substring(input.indexOf("(") + 1, input.lastIndexOf(")"));
      args = argString.split(",");
    } catch (Exception e) {
      return input;
    }

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

      // JSON parsing is expensive so cache the results in a Map against a hash of the input

      long hash = Hashing.murmur3_128().hashString(argString, StandardCharsets.UTF_8).asLong();
      ObjectNode json = jsonCache.get(hash);
      if (json == null) {
        json = (ObjectNode) new ObjectMapper().readTree(argString);
        jsonCache.put(hash, json);
      }
      return json;
    }

    if (input.startsWith("@ARRAY(")) {
      String[] parts = argString.split(",");
      int n = Integer.parseInt(parts[1]);
      // Cache the Processors
      DataGenProcessor subProcessor = processors.get(parts[0]);
      if (subProcessor == null) {
        // Propagate this run's oneupStart so @ONEUP fields inside the sub-generator get
        // the same non-colliding starting point as top-level @ONEUP fields when running
        // multiple instances in parallel with different oneupStart values (see README).
        // Derive the sub-generator's random seed from this generator's own rng (rather
        // than hard-coding 0) so its output also actually varies with the top-level
        // randomSeed, instead of every run/instance producing identical nested content.
        subProcessor =
            new DataGenProcessor(directoryPath + "/" + parts[0], oneupStart, rng.nextLong());
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

  private LocalDateTime getRandomDateTimeBetween(
      LocalDateTime startDateTime, LocalDateTime endDateTime) {
    // Calculate the number of days between startDate and endDate
    long minutesBetween = ChronoUnit.DAYS.between(startDateTime, endDateTime);
    // Generate a random number of days to add to the startDate
    long randomMinutes = rng.nextInt((int) minutesBetween + 1);
    // Return the result of adding the random number of days to startDate
    return startDateTime.plusMinutes(randomMinutes);
  }
}
