package com.mongodb.solcon.mxtest;

import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class MXTest {
  // Shared Gson instance with pretty printing enabled
  private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: GET|POST|DELETE <URL> [@file|string|fields]");
      System.exit(1);
    }

    HttpClient client = HttpClient.newHttpClient();
    String method = args[0].toUpperCase();

    HttpRequest request;
    if ("POST".equals(method)) {
      if (args.length < 3) {
        System.err.println("POST requires a body: @file or inline string");
        System.exit(1);
      }
      byte[] body = getBodyContent(args[2]);
      request =
          HttpRequest.newBuilder()
              .uri(new URI(args[1]))
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
    } else if ("DELETE".equals(method)) {
      request = HttpRequest.newBuilder().uri(new URI(args[1])).DELETE().build();
    } else {
      // Default to GET
      request = HttpRequest.newBuilder().uri(new URI(args[1])).GET().build();
    }

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      System.err.println("HTTP " + response.statusCode());
      if (response.body() != null && !response.body().isEmpty()) {
        System.err.println(formatJsonIfPossible(response.body()));
      }
      System.exit(1);
    } else {
      if ("GET".equals(method) && args.length >= 3) {
        System.out.println(filterJson(response.body(), args[2]));
      } else {
        System.out.println(formatJsonIfPossible(response.body()));
      }
    }
  }

  /**
   * Gets body content from either a file (prefixed with @) or inline string
   *
   * @param input either "@filename" or raw string content
   * @return byte array of content
   */
  private static byte[] getBodyContent(String input) throws Exception {
    if (input.startsWith("@")) {
      // File path - remove @ prefix and read file
      String filePath = input.substring(1);
      return Files.readAllBytes(Paths.get(filePath));
    } else {
      // Inline string content
      return input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  private static String formatJsonIfPossible(String body) {
    if (body == null || body.trim().isEmpty()) {
      return body;
    }

    // Try to parse as regular JSON first
    try {
      JsonElement json = JsonParser.parseString(body);
      return PRETTY_GSON.toJson(json);
    } catch (JsonSyntaxException e) {
      // If that fails, try to handle as NDJSON (newline-delimited JSON)
      return formatNdjson(body);
    }
  }

  private static String formatNdjson(String body) {
    String[] lines = body.split("\\r?\\n");
    StringBuilder result = new StringBuilder();

    for (String line : lines) {
      if (line.trim().isEmpty()) {
        continue;
      }
      try {
        JsonElement json = JsonParser.parseString(line);
        result.append(PRETTY_GSON.toJson(json));
        result.append(System.lineSeparator());
        result.append(System.lineSeparator()); // Extra line between objects
      } catch (JsonSyntaxException e) {
        result.append(line);
        result.append(System.lineSeparator());
      }
    }

    return result.toString();
  }

  private static String filterJson(String body, String fieldList) {
    if (body == null || body.trim().isEmpty()) {
      return body;
    }

    try {
      // Try to parse as a single JSON object or array
      JsonElement root = JsonParser.parseString(body);
      return filterJsonElement(root, fieldList);
    } catch (JsonSyntaxException e) {
      // If that fails, try to handle as NDJSON (newline-delimited JSON)
      return filterNdjson(body, fieldList);
    }
  }

  private static String filterNdjson(String body, String fieldList) {
    String[] lines = body.split("\\r?\\n");
    StringBuilder result = new StringBuilder();

    for (String line : lines) {
      if (line.trim().isEmpty()) {
        continue;
      }
      try {
        JsonElement element = JsonParser.parseString(line);
        String filtered = filterJsonElement(element, fieldList);
        result.append(filtered);
        // Don't add extra newline since filterJsonElement already includes one
      } catch (JsonSyntaxException e) {
        result.append(line);
        result.append(System.lineSeparator());
      }
    }

    return result.toString();
  }

  private static String filterJsonElement(JsonElement root, String fieldList) {
    String[] fields = fieldList.split(",");
    Map<String, List<String[]>> pathMap = new LinkedHashMap<>();

    for (String field : fields) {
      String[] parts = field.trim().split("\\.");
      pathMap
          .computeIfAbsent(parts[0], k -> new ArrayList<>())
          .add(parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0]);
    }

    if (root.isJsonObject()) {
      JsonObject filtered = filterObject(root.getAsJsonObject(), pathMap);
      return PRETTY_GSON.toJson(filtered) + System.lineSeparator();
    } else if (root.isJsonArray()) {
      JsonArray filteredArray = new JsonArray();
      for (JsonElement element : root.getAsJsonArray()) {
        if (element.isJsonObject()) {
          JsonObject filtered = filterObject(element.getAsJsonObject(), pathMap);
          filteredArray.add(filtered);
        } else {
          filteredArray.add(element);
        }
      }
      return PRETTY_GSON.toJson(filteredArray) + System.lineSeparator();
    } else {
      return PRETTY_GSON.toJson(root) + System.lineSeparator();
    }
  }

  private static JsonObject filterObject(JsonObject rootObj, Map<String, List<String[]>> pathMap) {
    JsonObject filtered = new JsonObject();
    for (Map.Entry<String, List<String[]>> entry : pathMap.entrySet()) {
      String rootField = entry.getKey();
      JsonElement value = rootObj.get(rootField);
      if (value == null) continue;

      if (entry.getValue().stream().allMatch(parts -> parts.length == 0)) {
        filtered.add(rootField, value);
      } else {
        if (value.isJsonObject()) {
          filtered.add(rootField, pickFields(value.getAsJsonObject(), entry.getValue()));
        } else if (value.isJsonArray()) {
          filtered.add(rootField, filterArray(value.getAsJsonArray(), entry.getValue()));
        }
      }
    }
    return filtered;
  }

  private static JsonObject pickFields(JsonObject obj, List<String[]> subpaths) {
    JsonObject result = new JsonObject();
    for (String[] parts : subpaths) {
      if (parts.length == 0) continue;
      String fieldName = parts[0];
      JsonElement subValue = obj.get(fieldName);
      if (subValue == null) continue;

      if (parts.length == 1) {
        result.add(fieldName, subValue);
      } else {
        if (subValue.isJsonObject()) {
          result.add(
              fieldName,
              pickFields(
                  subValue.getAsJsonObject(),
                  Collections.singletonList(Arrays.copyOfRange(parts, 1, parts.length))));
        } else if (subValue.isJsonArray()) {
          result.add(
              fieldName,
              filterArray(
                  subValue.getAsJsonArray(),
                  Collections.singletonList(Arrays.copyOfRange(parts, 1, parts.length))));
        }
      }
    }
    return result;
  }

  private static JsonArray filterArray(JsonArray array, List<String[]> subpaths) {
    JsonArray resultArray = new JsonArray();
    for (JsonElement element : array) {
      if (element.isJsonObject()) {
        resultArray.add(pickFields(element.getAsJsonObject(), subpaths));
      } else {
        resultArray.add(element);
      }
    }
    return resultArray;
  }
}
