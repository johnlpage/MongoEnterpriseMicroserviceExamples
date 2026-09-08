package com.johnlpage.datagen;

import com.fasterxml.jackson.databind.JsonNode;
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
    this(directoryPath, 0L, 0L);
  }

  DataGenProcessor(String directoryPath, long oneupStart, long randomSeed) throws IOException {
    readCsvFiles(directoryPath);
    random = new Random(randomSeed);
    valueMaker = new ValueMaker(random, directoryPath, oneupStart);
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

  List<JsonNode> generateJsonDocuments(int numberOfJsonDocuments) throws IOException {
    List<JsonNode> documentsGenerated = new ArrayList<>();

    for (int i = 0; i < numberOfJsonDocuments; i++) {
      ObjectNode jsonNode = objectMapper.createObjectNode();
      // If a "SCALAR" column is used (see setNode/README for details), the entire
      // generated document for this iteration becomes this raw scalar value instead
      // of the accumulated ObjectNode - used to build arrays of plain strings/numbers
      // via @ARRAY(subdirectory,n) rather than arrays of sub-objects.
      JsonNode scalarOverride = null;

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

            if (field.equals("SCALAR")) {
              // Special field name: the value of this column becomes the whole
              // generated "document" for this array element - a bare scalar - rather
              // than being nested as a field of an object. See README for details.
              scalarOverride = valueToJsonNode(value);
              continue;
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
      documentsGenerated.add(scalarOverride != null ? scalarOverride : jsonNode);
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

    } else if (value instanceof List<?> list) {
      ArrayNode arrayNode = objectMapper.createArrayNode();
      // Elements are always JsonNode: either whole sub-documents (ObjectNode, the
      // normal case) or bare scalars (e.g. TextNode/LongNode) when the sub-generator's
      // CSVs used the special "SCALAR" field name - see README for @ARRAY + SCALAR.
      for (Object item : list) {
        if (item instanceof JsonNode node) {
          arrayNode.add(node);
        }
      }

      where.set(key, arrayNode);
    } else {
      JsonNode node = valueToJsonNode(value);
      if (node != null) {
        where.set(key, node);
      }
    }
  }

  /**
   * Converts a raw generated value (String, Long, Double, Boolean, LocalDate,
   * LocalDateTime, ...) into the equivalent JsonNode, applying the same
   * numeric/boolean coercion rules used for ordinary object fields. Returns null for
   * values that should be omitted entirely (empty strings, the literal "null").
   */
  private JsonNode valueToJsonNode(Object value) {
    if (value instanceof JsonNode node) {
      return node;
    } else if (value instanceof Double d) {
      return objectMapper.getNodeFactory().numberNode(d);
    } else if (value instanceof Long l) {
      return objectMapper.getNodeFactory().numberNode(l);
    } else if (value instanceof Integer n) {
      return objectMapper.getNodeFactory().numberNode(n);
    } else if (value instanceof Boolean b) {
      return objectMapper.getNodeFactory().booleanNode(b);
    } else if (value instanceof LocalDate ld) {
      return objectMapper.getNodeFactory().textNode(ld.format(DateTimeFormatter.ISO_DATE));
    } else if (value instanceof LocalDateTime ld) {
      return objectMapper.getNodeFactory().textNode(ld.format(DateTimeFormatter.ISO_DATE));
    } else if (value instanceof String strValue) {
      if (hasUnsafeLeadingZero(strValue)) {
        // Values like "02150" (a ZIP code) are only digits but Long.parseLong would
        // silently strip the leading zero, so preserve them as strings instead.
        if (!strValue.isEmpty() && !strValue.equals("null")) {
          return objectMapper.getNodeFactory().textNode(strValue);
        }
        return null;
      }
      try {
        return objectMapper.getNodeFactory().numberNode(Long.parseLong(strValue));
      } catch (NumberFormatException e) {
        try {
          // the CSV parser considers everything as strings but in JS I'd like some to be numbers
          return objectMapper.getNodeFactory().numberNode(Double.parseDouble(strValue));
        } catch (NumberFormatException e2) {
          if (strValue.equals("true") || strValue.equals("false")) {
            return objectMapper.getNodeFactory().booleanNode(Boolean.parseBoolean(strValue));
          } else {
            if (!strValue.equals("") && !strValue.equals("null")) {
              // No Empty fields.
              return objectMapper.getNodeFactory().textNode(strValue);
            }
            return null;
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns true if the value is a purely-digit string (optionally signed) with a leading
   * zero and more than one digit, e.g. "02150" or "-0123". Such values would still
   * successfully parse as a Long, but doing so silently discards the leading zero(s), which
   * is data loss for values like ZIP codes that are conventionally numeric-looking text.
   * Decimal values such as "0.5" are unaffected since they aren't purely digits.
   */
  private boolean hasUnsafeLeadingZero(String value) {
    String digits = value;
    if (digits.startsWith("-") || digits.startsWith("+")) {
      digits = digits.substring(1);
    }
    if (digits.length() <= 1 || digits.charAt(0) != '0') {
      return false;
    }
    for (int i = 0; i < digits.length(); i++) {
      if (!Character.isDigit(digits.charAt(i))) {
        return false;
      }
    }
    return true;
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
