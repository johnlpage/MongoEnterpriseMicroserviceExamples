package com.johnlpage.mews.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ObjectConverter {

  private static final List<String> DATE_FORMATS = new ArrayList<>();

  static {
    // Add various date formats you want to support
    DATE_FORMATS.add("yyyy-MM-dd'T'HH:mm:ss"); // ISO 8601 with seconds
    DATE_FORMATS.add("yyyy-MM-dd"); // Basic date format
    DATE_FORMATS.add("yyyy-MM-dd'T'HH:mm:ss.SSSX"); // ISO 8601 with milliseconds and timezone
    DATE_FORMATS.add("yyyy-MM-dd'T'HH:mm:ssX"); // ISO 8601 with seconds and timezone
    DATE_FORMATS.add("MM/dd/yyyy"); // US date format
    // DATE_FORMATS.add("dd/MM/yyyy");                 // European date format
    // Add more formats as needed
  }

  public static Object convertObject(Object input) {
    if (input instanceof Number) {
      return ((Number) input).doubleValue();
    }
    if (input instanceof String) {
      for (String format : DATE_FORMATS) {
        try {
          return parseDate((String) input, format);
        } catch (ParseException e) {
          // Continue to the next format
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
    // For other unhandled types, return as is
    return input;
  }

  private static Date parseDate(String dateStr, String format) throws ParseException {
    SimpleDateFormat sdf = new SimpleDateFormat(format);
    sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // Set timezone if needed
    return sdf.parse(dateStr);
  }
}
