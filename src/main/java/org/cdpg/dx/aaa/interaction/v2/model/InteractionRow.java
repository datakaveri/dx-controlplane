package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonObject;
import java.util.Map;
import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record InteractionRow(
    String entityId,
    EntityType entityType,
    boolean isBookmarked,
    boolean isLiked,
    boolean isDisliked)
    implements BaseEntity<InteractionRow> {

  public static InteractionRow fromJson(JsonObject json) {
    return new InteractionRow(
        json.getString("entity_id"),
        EntityType.valueOf(json.getString("entity_type")),
        json.getBoolean("is_bookmarked"),
        json.getBoolean("is_liked"),
        json.getBoolean("is_disliked"));
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
