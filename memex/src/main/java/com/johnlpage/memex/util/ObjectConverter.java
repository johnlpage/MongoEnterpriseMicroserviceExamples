package com.johnlpage.memex.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ObjectConverter {

  private static final List<String> DATE_FORMATS = new ArrayList<>();
  private static final List<SimpleDateFormat> dateFormatters = new ArrayList<>();

  static {
    // Add various date formats you want to support
    DATE_FORMATS.add("yyyy-MM-dd"); // Basic date format
    DATE_FORMATS.add("yyyy-MM-dd'T'HH:mm:ss"); // ISO 8601 with seconds
    // DATE_FORMATS.add("MM/dd/yyyy");                 // US date format
    //  DATE_FORMATS.add("dd/MM/yyyy");                 // European date format
    DATE_FORMATS.add("yyyy-MM-dd'T'HH:mm:ss.SSSX"); // ISO 8601 with milliseconds and timezone
    DATE_FORMATS.add("yyyy-MM-dd'T'HH:mm:ssX"); // ISO 8601 with seconds and timezone
    // Add more formats as needed

    for (String format : DATE_FORMATS) {
      SimpleDateFormat formatter = new SimpleDateFormat(format);
      formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
      dateFormatters.add(formatter);
    }
  }

  public static Object convertObject(Object input) {
    if (input instanceof Number) {
      return ((Number) input).doubleValue();
    }
    if (input instanceof String str) {
      // Optimisation given the formats we support
      if (!str.isEmpty() && str.length() >= 8 && Character.isDigit(str.charAt(0))) {
        for (SimpleDateFormat sdf : dateFormatters) {
          try {
            return sdf.parse(str);
          } catch (ParseException e) {
            // Continue to the next format
          } // DATE_FORMATS.add("dd/MM/yyyy");                 // European date format
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
}
