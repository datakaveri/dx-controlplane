package org.cdpg.dx.aaa.conversation.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.util.HashMap;
import java.util.Map;

public record RequestTypeMapping(
    Integer id, String typeKey, String displayName, Boolean isActive, String createdAt)
    implements BaseEntity<RequestTypeMapping> {

  public static RequestTypeMapping fromJson(JsonObject json) {
    if (json == null) {
      return null;
    }
    return new RequestTypeMapping(
        json.getInteger("id"),
        json.getString("type_key"),
        json.getString("display_name"),
        json.getBoolean("is_active"),
        json.getString("created_at"));
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (id != null) json.put("id", id);
    if (typeKey != null) json.put("type_key", typeKey);
    if (displayName != null) json.put("display_name", displayName);
    if (isActive != null) json.put("is_active", isActive);
    if (createdAt != null) json.put("created_at", createdAt);
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (id != null) map.put("id", id);
    if (typeKey != null) map.put("type_key", typeKey);
    if (displayName != null) map.put("display_name", displayName);
    if (isActive != null) map.put("is_active", isActive);
    return map;
  }

  @Override
  public String getTableName() {
    return "";
  }
}
