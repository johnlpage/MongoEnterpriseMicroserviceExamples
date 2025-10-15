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
  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: GET|POST <URL> [file|fields]");
      System.exit(1);
    }

    HttpClient client = HttpClient.newHttpClient();
    boolean isPost = "POST".equalsIgnoreCase(args[0]);

    HttpRequest request;
    if (isPost) {
      byte[] body = Files.readAllBytes(Paths.get(args[2]));
      request =
          HttpRequest.newBuilder()
              .uri(new URI(args[1]))
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
    } else {
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
      if (!isPost && args.length >= 3) {
        System.out.println(filterJson(response.body(), args[2]));
      } else {
        System.out.println(formatJsonIfPossible(response.body()));
      }
    }
  }

  private static String formatJsonIfPossible(String body) {
    try {
      JsonElement json = JsonParser.parseString(body);
      return new GsonBuilder().setPrettyPrinting().create().toJson(json);
    } catch (JsonSyntaxException e) {
      return body;
    }
  }

  private static String filterJson(String body, String fieldList) {
    try {
      JsonElement root = JsonParser.parseString(body);
      String[] fields = fieldList.split(",");
      Map<String, List<String[]>> pathMap = new LinkedHashMap<>();

      for (String field : fields) {
        String[] parts = field.trim().split("\\.");
        pathMap
            .computeIfAbsent(parts[0], k -> new ArrayList<>())
            .add(parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0]);
      }

      Gson gson = new GsonBuilder().setPrettyPrinting().create();

      if (root.isJsonObject()) {
        JsonObject filtered = filterObject(root.getAsJsonObject(), pathMap);
        return gson.toJson(filtered);
      } else if (root.isJsonArray()) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement element : root.getAsJsonArray()) {
          if (element.isJsonObject()) {
            JsonObject filtered = filterObject(element.getAsJsonObject(), pathMap);
            sb.append(gson.toJson(filtered)).append(System.lineSeparator());
          } else {
            sb.append(gson.toJson(element)).append(System.lineSeparator());
          }
        }
        return sb.toString();
      } else {
        return gson.toJson(root);
      }
    } catch (Exception e) {
      return body;
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
