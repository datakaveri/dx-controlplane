package org.cdpg.dx.aaa.bookmarks.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record Bookmark(
    UUID id, UUID userId, UUID entityId, BookmarkEntityType entityType, String createdAt)
    implements BaseEntity<Bookmark> {

  public static Bookmark fromJson(JsonObject json) {
    return new Bookmark(
        json.getString("id") != null ? UUID.fromString(json.getString("id")) : null,
        UUID.fromString(json.getString("user_id")),
        UUID.fromString(json.getString("entity_id")),
        BookmarkEntityType.valueOf(json.getString("entity_type")),
        json.getString("created_at"));
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (id != null) json.put("id", id.toString());
    json.put("user_id", userId.toString());
    json.put("entity_id", entityId.toString());
    json.put("entity_type", entityType.name());
    if (createdAt != null) json.put("created_at", createdAt);
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("user_id", userId.toString());
    map.put("entity_id", entityId.toString());
    map.put("entity_type", entityType.name());
    return map;
  }

  @Override
  @JsonIgnore
  public String getTableName() {
    return "bookmarks";
  }
}
