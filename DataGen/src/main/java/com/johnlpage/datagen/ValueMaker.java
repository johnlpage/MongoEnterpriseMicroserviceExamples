package com.johnlpage.datagen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
  // Each @ONEUP column (keyed by "CSV filename:field name") has its own independent
  // counter - @ONEUP is no longer a shared sequence. Counters are created lazily on
  // first evaluation; each starts at 1, or at the oneupStart supplied on the command
  // line (oneupStart defaults to 0, which is treated as "not supplied" - see README).
  Map<String, Long> oneupCounters = new HashMap<>();
  // The run-wide oneupStart passed in on the command line. Kept separately from the
  // per-column counters so that when a new @ARRAY sub-generator/ValueMaker is lazily
  // created (see @ARRAY handling below) it can be seeded with the *original* oneupStart,
  // and so that every per-column @ONEUP counter in this generator starts from the same
  // process-wide oneupStart. This is what makes `oneupStart` protect against @ONEUP
  // collisions across multiple parallel instances, even for @ONEUP fields nested inside
  // @ARRAY sub-directories.
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
    this.oneupStart = oneupStart;
    processors = new HashMap<>();
    jsonCache = new HashMap<>();
  }

  Object expandValue(String input, String sequenceId) throws IOException {
    if (!input.startsWith("@")) {
      return input;
    }
    if (input.equals("@ONEUP")) {
      // Independent counter per sequence (CSV column), starting at 1 - or at the
      // oneupStart supplied on the command line (0 means "not supplied", so start at 1).
      long start = oneupStart == 0 ? 1L : oneupStart;
      return oneupCounters.merge(sequenceId, start, (current, unused) -> current + 1);
    }

    String[] args;
    String argString;
    try {
      argString = input.substring(input.indexOf("(") + 1, input.lastIndexOf(")"));
      args = argString.split(",");
    } catch (Exception e) {
      return input;
    }

    if (input.startsWith("@ONEUP(")) {
      // String form of @ONEUP for prefixed, zero-padded identifiers, e.g.
      // @ONEUP(cus,8) -> "cus00001522". Uses the same per-column counter
      // sequence and starting value (1, or oneupStart) as the plain form.
      long start = oneupStart == 0 ? 1L : oneupStart;
      Long oneup = oneupCounters.merge(sequenceId, start, (current, unused) -> current + 1);
      String prefix = args[0].trim();
      if (args.length > 1) {
        int width = Integer.parseInt(args[1].trim());
        if (width > 0) {
          return prefix + String.format("%0" + width + "d", oneup);
        }
      }
      return prefix + oneup;
    }

    if (input.startsWith("@INTEGER(")) {
      int from = Integer.parseInt(args[0]);
      int to = Integer.parseInt(args[1]);
      return rng.nextInt(to - from + 1) + from;
    }

    if (input.startsWith("@DOUBLE(")) {
      double to = Double.parseDouble(args[0]);
      double from = Double.parseDouble(args[1]);
      // Round to 2 decimal places - typical for prices/monetary-style test data -
      // avoiding binary floating-point artefacts like 0.7000000000000001.
      return Math.round(((rng.nextDouble() * (to - from)) + from) * 100.0) / 100.0;
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

    if (input.startsWith("@STRING(")) {
      // Force this cell's value to be a JSON string, bypassing the usual
      // numeric/boolean coercion of all-digit cells (e.g. @STRING(95814) stays
      // "95814"). The raw text between the parentheses is used verbatim - no
      // JSON de-quoting - the same arg extraction as @JSON, so commas and
      // embedded ')' work. An empty @STRING() omits the field (null), matching
      // the "no empty fields" rule for plain empty cells. Returning a TextNode
      // means valueToJsonNode's JsonNode passthrough keeps it a string.
      if (argString.isEmpty()) {
        return null;
      }
      return JsonNodeFactory.instance.textNode(argString);
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
    // Randomise to whole-minute precision, inclusive of both bounds. Use nextDouble
    // rather than nextInt so ranges longer than Integer.MAX_VALUE minutes (~4000
    // years) don't overflow the int bound of Random.nextInt.
    long minutesBetween = ChronoUnit.MINUTES.between(startDateTime, endDateTime);
    long randomMinutes = (long) (rng.nextDouble() * (minutesBetween + 1));
    return startDateTime.plusMinutes(randomMinutes);
  }
}
