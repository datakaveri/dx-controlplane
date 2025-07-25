package org.cdpg.dx.common.util;

import org.cdpg.dx.common.exception.DxValidationException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class DateTimeHelper {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Returns current time as String using default pattern
    public static String getCurrentTimeString() {
        return LocalDateTime.now().format(FORMATTER);
    }

    // Returns current time as String with a custom pattern
    public static String getCurrentTimeString(String pattern) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    // Parse string to LocalDateTime using default FORMATTER
    public static Optional<LocalDateTime> parse(String dateTimeStr) {
        try {
            return Optional.of(LocalDateTime.parse(dateTimeStr, FORMATTER));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Format LocalDateTime to String using default FORMATTER
    public static String format(LocalDateTime dateTime) {
        return dateTime.format(FORMATTER);
    }

    // Parse string to LocalDateTime directly (nullable) using default FORMATTER
    public static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDateTime.parse(value, FORMATTER);
    }

  public static  LocalDateTime parseRequiredDateTime(String value,String name) {
      if (value == null || value.isBlank())
        throw new DxValidationException("required timestamp missing for"+name);
      try {
        return LocalDateTime.parse(value, FORMATTER);
      }
      catch(Exception e)
      {
        throw new DxValidationException("Parsing of LocalDateTime unsuccessful!");
      }
  }
}
