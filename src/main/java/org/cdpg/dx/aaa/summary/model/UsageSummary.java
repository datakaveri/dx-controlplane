package org.cdpg.dx.aaa.summary.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.util.Map;

public record UsageSummary(String description, long count, long size)
    implements BaseEntity<UsageSummary> {

  public static UsageSummary fromJson(JsonObject json) {
    if (json == null) return null;
    String description = json.getString("description");
    long count = toLong(json.getValue("count"));
    long size = toLong(json.getValue("size"));
    return new UsageSummary(description, count, size);
  }

  private static long toLong(Object value) {
    if (value == null) return 0L;
    if (value instanceof Number) return ((Number) value).longValue();
    if (value instanceof String) {
      try {
        return Long.parseLong((String) value);
      } catch (NumberFormatException e) {
        return 0L;
      }
    }
    return 0L;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    return Map.of();
  }

  @Override
  public JsonObject toJson() {
    return null;
  }

  @Override
  public String getTableName() {
    return "";
  }
}
