package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonObject;

import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.util.Map;

public class InteractionRow implements BaseEntity<InteractionRow> {

  private final String assetId;
  private final EntityType assetType;
  private final boolean isBookmarked;
  private final boolean isLiked;
  private final boolean isDisliked;

  private transient ItemSummary item;

  public InteractionRow(
      String assetId,
      EntityType assetType,
      boolean isBookmarked,
      boolean isLiked,
      boolean isDisliked) {
    this.assetId = assetId;
    this.assetType = assetType;
    this.isBookmarked = isBookmarked;
    this.isLiked = isLiked;
    this.isDisliked = isDisliked;
  }

  public String getAssetId() {
    return assetId;
  }

  public EntityType getAssetType() {
    return assetType;
  }

  public boolean isBookmarked() {
    return isBookmarked;
  }

  public boolean isLiked() {
    return isLiked;
  }

  public boolean isDisliked() {
    return isDisliked;
  }

  public ItemSummary getAssetDetails() {
    return item;
  }

  public InteractionRow withItem(ItemSummary item) {
    this.item = item;
    return this;
  }

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
    JsonObject json =
        new JsonObject()
            .put("assetId", assetId)
            .put("assetType", assetType)
            .put("isBookmarked", isBookmarked)
            .put("isLiked", isLiked)
            .put("isDisliked", isDisliked);

    if (item != null) {
      json.put("item", JsonObject.mapFrom(item));
    }

    return json;
  }

  @Override
  public String getTableName() {
    return null;
  }
}
