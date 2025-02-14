package com.johnlpage.datagen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class DataGenProcessor {

  private final Map<String, List<CSVRecord>> csvData = new HashMap<>();
  private final Map<String, List<String>> fieldNames = new HashMap<>();
  private final Map<String, TreeSet<CSVLine>> csvTrees = new HashMap<>();
  private final Map<String, Integer> maxProbability = new HashMap<>();
  ValueMaker valueMaker;
  ObjectMapper objectMapper;
  Random random;

  DataGenProcessor(String directoryPath) throws IOException {
    readCsvFiles(directoryPath);
    random = new Random(0);
    valueMaker = new ValueMaker(random, directoryPath);
    objectMapper = new ObjectMapper();
    buildLookupTree();
  }

  private static CSVParser getCsvRecords(File file) throws IOException {
    FileInputStream fileInputStream = new FileInputStream(file);
    InputStreamReader inputStreamReader;
    if (file.getName().endsWith(".gz")) {
      GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
      inputStreamReader = new InputStreamReader(gzipInputStream);
    } else {
      inputStreamReader = new InputStreamReader(fileInputStream);
    }
    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
    CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();

    return new CSVParser(bufferedReader, format);
  }

  List<ObjectNode> generateJsonDocuments(int numberOfJsonDocuments) throws IOException {
    List<ObjectNode> documentsGenerated = new ArrayList<>();

    for (int i = 0; i < numberOfJsonDocuments; i++) {
      ObjectNode jsonNode = objectMapper.createObjectNode();

      for (Map.Entry<String, Integer> entry : maxProbability.entrySet()) {
        int totalProbability = entry.getValue();
        int randomValue = (int) Math.floor(random.nextDouble() * totalProbability);
        TreeSet<CSVLine> csvTree = csvTrees.get(entry.getKey());
        CSVLine chosen = csvTree.higher(new CSVLine(randomValue, null));

        CSVRecord record = Objects.requireNonNull(chosen).getCsvRecord();
        for (String field : fieldNames.get(entry.getKey())) {
          if (!field.equals("probability")) {
            Object value;
            String asString = record.get(field);
            if (asString.startsWith("@")) {
              value = valueMaker.expandValue(asString);
            } else {
              value = asString;
            }
            // Nested values
            if (field.contains(".")) {
              String[] parts = field.split("\\.");
              ObjectNode here = jsonNode;
              int depth = 0;
              for (String part : parts) {
                depth++;
                if (depth < parts.length) {
                  here.putIfAbsent(part, objectMapper.createObjectNode());
                  here = (ObjectNode) here.get(part);
                } else {

                  setNode(here, part, value);
                }
              }
            } else {
              setNode(jsonNode, field, value);
            }
          }
        }
      }
      documentsGenerated.add(jsonNode);
      /*  System.out.println(
      objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));*/
    }
    return documentsGenerated;
  }

  private void setNode(ObjectNode where, String key, Object value) {
    if (value instanceof ObjectNode) {
      // if the key is "ROOT" then replace don't add
      if (key.equals("ROOT")) {
        where.removeAll();
        where.setAll((ObjectNode) value);
      } else {
        where.set(key, (ObjectNode) value);
      }

    } else if (value instanceof List) {
      ArrayNode arrayNode = objectMapper.createArrayNode();
      // Add all ObjectNodes from the list to the ArrayNode
      //noinspection unchecked
      for (ObjectNode objectNode : (List<ObjectNode>) value) {
        arrayNode.add(objectNode);
      }

      where.set(key, arrayNode);
    } else if (value instanceof Double) {
      where.put(key, (Double) value);
    } else if (value instanceof Long) {
      where.put(key, (Long) value);
    } else if (value instanceof Integer) {
      where.put(key, (Integer) value);
    } else if (value instanceof Boolean) {
      where.put(key, (Boolean) value);
    } else if (value instanceof LocalDate ld) {
      where.put(key, ld.format(DateTimeFormatter.ISO_DATE));
    } else if (value instanceof LocalDateTime ld) {
      where.put(key, ld.format(DateTimeFormatter.ISO_DATE));
    } else if (value instanceof String) {
      try {
        where.put(key, Long.parseLong((String) value));
      } catch (NumberFormatException e) {
        try {
          // the CSV parser considers everything as strings but in JS I'd like some to be numbers
          where.put(key, Double.parseDouble((String) value));
        } catch (NumberFormatException e2) {

          if (value.equals("true") || value.equals("false")) {
            where.put(key, Boolean.parseBoolean((String) value));
          } else {
            if (!value.equals("") && !value.equals("null")) {
              // No Empty fields.
              where.put(key, (String) value);
            }
          }
        }
      }
    }
  }

  // Read the CSV Files into a has of Lists
  void readCsvFiles(String directoryPath) throws IOException {

    File directory = new File(directoryPath);
    if (!directory.exists()) {
      System.out.println("Directory " + directoryPath + " does not exist");
      System.exit(1);
    }
    File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && (file.getName().endsWith(".gz") || file.getName().endsWith(".csv"))) {
          String filename = file.getName();

          CSVParser parser = getCsvRecords(file);
          List<CSVRecord> records = parser.getRecords();

          if (!records.isEmpty()) {
            csvData.put(filename, records);
          }
          fieldNames.put(filename, parser.getHeaderNames());
        }
      }
    } else {
      System.out.println("No files found in " + directoryPath);
      System.exit(0);
    }
  }

  /**
   * For each CSV Files, compute the total of the probability column and also A cumulative value we
   * can use to find a specific element, for this we use a TreeSet Which is a Red/Black tree that's
   * best to find things in when using < and >
   */
  void buildLookupTree() {
    for (Map.Entry<String, List<CSVRecord>> entry : csvData.entrySet()) {
      String fName = entry.getKey();
      List<CSVRecord> records = entry.getValue();
      TreeSet<CSVLine> lineSet =
          new TreeSet<>(Comparator.comparingInt(CSVLine::getCumulativeProbability));
      int cumulativeProbability = 0;
      for (CSVRecord record : records) {
        int probability = (int) Double.parseDouble(record.get("probability"));
        cumulativeProbability += probability;
        CSVLine line = new CSVLine(cumulativeProbability, record);
        lineSet.add(line);
      }
      csvTrees.put(fName, lineSet);
      maxProbability.put(fName, cumulativeProbability);
    }
  }
}
