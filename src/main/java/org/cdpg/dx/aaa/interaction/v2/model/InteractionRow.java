package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonObject;
import java.util.Map;
import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record InteractionRow(
    String assetId, EntityType assetType, boolean isBookmarked, boolean isLiked, boolean isDisliked)
    implements BaseEntity<InteractionRow> {

  public static InteractionRow fromJson(JsonObject json) {
    return new InteractionRow(
        json.getString("asset_id"),
        EntityType.valueOf(json.getString("asset_type")),
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
    throw new UnsupportedOperationException(
        "InteractionRow is a DB projection and must not be serialized to API JSON");
  }

  @Override
  public String getTableName() {
    return "";
  }
}
