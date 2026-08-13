package org.cdpg.dx.aaa.interaction.v2.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
      json.getString("user_id") != null ? UUID.fromString(json.getString("user_id")) : null,      // ← add null check
      json.getString("asset_id") != null ? UUID.fromString(json.getString("asset_id")) : null,    // ← add null check
      json.getString("type") != null ? ProviderFeedbackType.valueOf(json.getString("type")) : null,
      json.getJsonObject("data"),
      json.getString("created_at") != null ? LocalDateTime.parse(json.getString("created_at")) : null,
      json.getString("updated_at") != null ? LocalDateTime.parse(json.getString("updated_at")) : null
    );
  }

  public static ProviderFeedback fromRequestJson(JsonObject json) {
    return new ProviderFeedback(
        json.getString("id") != null ? UUID.fromString(json.getString("id")) : null,
        json.getString("userId") != null ? UUID.fromString(json.getString("userId")) : null,
        json.getString("assetId") != null ? UUID.fromString(json.getString("assetId")) : null,
        json.getString("type") != null
            ? ProviderFeedbackType.valueOf(json.getString("type"))
            : null,
        json.getJsonObject("data"),
        json.getString("createdAt") != null
            ? LocalDateTime.parse(json.getString("createdAt"))
            : null,
        json.getString("updatedAt") != null
            ? LocalDateTime.parse(json.getString("updatedAt"))
            : null);
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    map.put("user_id", userId.toString());
    map.put("asset_id", assetId.toString());
    map.put("type", type.name());
    map.put("data", data);
    if (createdAt != null) map.put("created_at", createdAt.toString());
    if (updatedAt != null) map.put("updated_at", updatedAt.toString());

    return map;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject()
      .put("userId", userId.toString())
      .put("assetId", assetId.toString())
      .put("type", type.name())
      .put("data", data);
    if (createdAt != null) json.put("createdAt", createdAt.toString());
    if (updatedAt != null) json.put("updatedAt", updatedAt.toString());
    return json;
  }

  @Override
  @JsonIgnore
  public String getTableName() {
    return "provider_feedback";
  }
}
