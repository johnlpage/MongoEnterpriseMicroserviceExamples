package com.johnlpage.memex.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class ObjectConverter {

  // DateTimeFormatter is thread-safe!
  private static final List<DateTimeFormatter> DATE_FORMATTERS = new ArrayList<>();

  // Add esoteric like MM/dd/yyyy in here if needed

  static {
    DATE_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    DATE_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    DATE_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"));
    DATE_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"));
  }

  public static Object convertObject(Object input) {
    if (input instanceof Number) {
      return ((Number) input).doubleValue();
    }
    if (input instanceof String str) {
      if (!str.isEmpty() && str.length() >= 8 && Character.isDigit(str.charAt(0))) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
          try {
            // Try parsing as different date/time types
            if (str.contains("T")) {
              if (str.contains("Z") || str.contains("+") || str.contains("-")) {
                return Date.from(ZonedDateTime.parse(str, formatter).toInstant());
              } else {
                return Date.from(
                    LocalDateTime.parse(str, formatter)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toInstant());
              }
            } else {
              return Date.from(
                  LocalDate.parse(str, formatter)
                      .atStartOfDay(java.time.ZoneOffset.UTC)
                      .toInstant());
            }
          } catch (DateTimeParseException e) {
            // Continue to next formatter
          }
        }
      }
      return input;
    }
    if (input instanceof Map) {
      Map<String, Object> map = new HashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) input).entrySet()) {
        map.put((String) entry.getKey(), convertObject(entry.getValue()));
      }
      return map;
    }
    if (input instanceof List) {
      List<Object> list = new ArrayList<>();
      for (Object item : (List<?>) input) {
        list.add(convertObject(item));
      }
      return list;
    }
    return input;
  }
}
