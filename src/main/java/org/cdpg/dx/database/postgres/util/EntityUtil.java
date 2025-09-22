package org.cdpg.dx.database.postgres.util;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EntityUtil {
  private static final Logger LOGGER = LogManager.getLogger(EntityUtil.class);

  public static void putIfPresent(Map<String, Object> map, String key, Optional<?> value) {
    value.ifPresent(v -> map.put(key, v));
  }

  public static void putIfPresent(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  public static void putIfNonEmpty(Map<String, Object> map, String key, String value) {
    if (value != null && !value.isEmpty()) {
      map.put(key, value);
    }
  }

  public static <T> void putIfNonEmpty(Map<String, Object> map, String key, T value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  public static UUID parseUUID(String value, String name) {
    if (value != null && !value.isEmpty()) {
      return UUID.fromString(value);
    }
    //LOGGER.warn("requested UUID is null or empty for {}", name);
    return null;
  }
}
