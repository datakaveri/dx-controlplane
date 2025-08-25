package org.cdpg.dx.aaa.activity.validator;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import org.cdpg.dx.aaa.activity.model.ActivityLogRequest;
import org.cdpg.dx.common.exception.DxBadRequestException;

public class ActivityLogRequestValidator {

  public static Optional<ActivityLogRequest> validateAndExtractAdminActivityParams(
      RoutingContext ctx, UUID overrideUserId) {
    try {
      HttpServerRequest req = ctx.request();

      // User ID from override or query param
      UUID userId = overrideUserId;
      String userIdStr = req.getParam("userId");
      if (userId == null && userIdStr != null && !userIdStr.isBlank()) {
        userId = UUID.fromString(userIdStr);
      }

      // --- starttime and endtime (as strings)
      String startRaw = req.getParam("starttime");
      String endRaw = req.getParam("endtime");

      // Enforce both-or-none rule
      boolean hasStart = startRaw != null && !startRaw.isBlank();
      boolean hasEnd = endRaw != null && !endRaw.isBlank();
      if (hasStart ^ hasEnd) {
        throw new IllegalArgumentException("Both starttime and endtime must be provided together");
      }

      Instant startTime = parseAsUtcLocalDateTime(startRaw);
      Instant endTime = parseAsUtcLocalDateTime(endRaw);

      if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
        throw new IllegalArgumentException("starttime must be before or equal to endtime");
      }

      // --- size
      int size = 1000;
      String sizeStr = req.getParam("size");
      if (sizeStr != null && !sizeStr.isBlank()) {
        size = Integer.parseInt(sizeStr);
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
      }

      // --- page
      int page = 1;
      String pageStr = req.getParam("page");
      if (pageStr != null && !pageStr.isBlank()) {
        page = Integer.parseInt(pageStr);
        if (page <= 0) throw new IllegalArgumentException("page must be >= 1");
      }

      return Optional.of(new ActivityLogRequest(userId, startRaw, endRaw, size, page));
    } catch (IllegalArgumentException | DateTimeParseException | NullPointerException e) {
      ctx.fail(new DxBadRequestException(e.getMessage()));
      return Optional.empty();
    }
  }

  /**
   * Parses a timestamp string into an Instant, supporting both ISO8601 with and without timezone.
   */
  private static Instant parseAsUtcLocalDateTime(String input) {
    if (input == null || input.isBlank()) return null;

    try {
      // Strict ISO format, without Z or timezone offset
      LocalDateTime local = LocalDateTime.parse(input, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      return local.atZone(ZoneOffset.UTC).toInstant();
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("Invalid datetime format");
    }
  }
}
