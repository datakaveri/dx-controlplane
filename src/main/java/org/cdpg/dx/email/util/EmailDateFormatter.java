package org.cdpg.dx.email.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Formats timestamps for display in emails. Timestamps such as an access request's {@code
 * expiryAt} are stored and transported as zone-less UTC {@link LocalDateTime} strings; showing
 * them as-is made recipients read UTC as local time (e.g. an expiry at 11:59 PM IST showed as
 * 6:29 PM).
 */
public final class EmailDateFormatter {

  private static final Logger LOGGER = LogManager.getLogger(EmailDateFormatter.class);

  public static final ZoneId DEFAULT_DISPLAY_ZONE = ZoneId.of("Asia/Kolkata");

  // e.g. "13-Nov-2026 23:59" (24-hour, in the display zone)
  private static final DateTimeFormatter DISPLAY_FORMAT =
      DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);

  private EmailDateFormatter() {}

  /**
   * Converts a UTC ISO local date-time (e.g. {@code 2026-11-13T18:29:59}) to {@code zone} and
   * formats it for humans. Returns an empty string for null/blank input and the raw value if it
   * cannot be parsed, so a formatting problem never blocks the email.
   */
  public static String formatUtcForDisplay(String utcLocalDateTime, ZoneId zone) {
    if (utcLocalDateTime == null || utcLocalDateTime.isBlank()) {
      return "";
    }
    try {
      return LocalDateTime.parse(utcLocalDateTime.trim())
          .atOffset(ZoneOffset.UTC)
          .atZoneSameInstant(zone)
          .format(DISPLAY_FORMAT);
    } catch (DateTimeParseException e) {
      LOGGER.warn("Could not parse date-time '{}' for email display", utcLocalDateTime);
      return utcLocalDateTime;
    }
  }
}
