package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.cdpg.dx.aaa.interaction.v2.enums.ProviderFeedbackType;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record ProviderFeedback(
  UUID id,
  UUID userId,
  UUID assetId,
  ProviderFeedbackType type,
  JsonObject data,
  LocalDateTime createdAt,
  LocalDateTime updatedAt)
  implements BaseEntity<ProviderFeedback> {

  public static ProviderFeedback fromJson(JsonObject json) {
    return new ProviderFeedback(
      json.getString("id") != null ? UUID.fromString(json.getString("id")) : null,
      UUID.fromString(json.getString("user_id")),
      UUID.fromString(json.getString("asset_id")),
      ProviderFeedbackType.valueOf(json.getString("type")),
      json.getJsonObject("data"),
      json.getString("created_at") != null ? LocalDateTime.parse(json.getString("created_at")) : null,
      json.getString("updated_at") != null ? LocalDateTime.parse(json.getString("updated_at")) : null);
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    map.put("user_id", userId.toString());
    map.put("asset_id", assetId.toString());
    map.put("type", type.name());
    map.put("data", data.encode());
    if (createdAt != null) map.put("created_at", createdAt.toString());
    if (updatedAt != null) map.put("updated_at", updatedAt.toString());

    return map;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject()
      .put("user_id", userId.toString())
      .put("asset_id", assetId.toString())
      .put("type", type.name())
      .put("data", data);
    if (createdAt != null) json.put("created_at", createdAt.toString());
    if (updatedAt != null) json.put("updated_at", updatedAt.toString());
    return json;
  }

  @Override
  public String getTableName() {
    return "provider_feedback";
  }
}
