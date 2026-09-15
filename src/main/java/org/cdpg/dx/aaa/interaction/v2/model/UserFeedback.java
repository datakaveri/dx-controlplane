package org.cdpg.dx.aaa.interaction.v2.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record UserFeedback(
    UUID id,
    UUID userId,
    UUID assetId,
    String assetType,
    Integer entityRating,
    @JsonProperty("actionSubtype") String actionSubType,
    @JsonProperty("actionSubdata") JsonObject actionSubData,
    LocalDateTime ratingCreatedAt,
    LocalDateTime ratingUpdatedAt,
    @JsonProperty("feedbackStatus") String feedbackStatus,
    @JsonProperty("feedbackComment") String feedbackComment,
    @JsonProperty("feedbackStatusUpdatedAt") LocalDateTime feedbackStatusUpdatedAt)
    implements BaseEntity<UserFeedback> {

  public static UserFeedback fromJson(JsonObject json) {
    return new UserFeedback(
        getUuid(json, "id"),
        getUuid(json, "user_id"),
        getUuid(json, "asset_id"),
        json.getString("asset_type"),
        json.getInteger("entity_rating"),
        json.getString("action_subtype"),
        json.getJsonObject("action_subdata"),
        getLocalDateTime(json, "feedback_created_at"),
        getLocalDateTime(json, "feedback_updated_at"),
        getFeedbackStatus(json),
        json.getString("feedback_comment"),
        getLocalDateTime(json, "feedback_status_updated_at"));
  }

  public static UserFeedback fromRequestJson(JsonObject json) {
    return new UserFeedback(
        getUuid(json, "id"),
        getUuid(json, "userId"),
        getUuid(json, "assetId"),
        json.getString("assetType"),
        json.getInteger("entityRating"),
        json.getString("actionSubtype"),
        json.getJsonObject("actionSubdata"),
        null,
        null,
        null,
        json.getString("feedbackComment", null),
        null);
  }

  private static UUID getUuid(JsonObject json, String field) {
    String value = json.getString(field);
    return value != null ? UUID.fromString(value) : null;
  }

  private static LocalDateTime getLocalDateTime(JsonObject json, String field) {
    String value = json.getString(field);
    return value != null ? LocalDateTime.parse(value) : null;
  }

  private static String getFeedbackStatus(JsonObject json) {
    return json.getString("feedback_status");
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (userId != null) {
      map.put("user_id", userId.toString());
    }

    if (assetId != null) {
      map.put("asset_id", assetId.toString());
    }

    if (assetType != null) {
      map.put("asset_type", assetType);
    }

    if (entityRating != null) {
      map.put("entity_rating", entityRating);
    }

    if (actionSubType != null) {
      map.put("action_subtype", actionSubType);
    }

    if (actionSubData != null) {
      map.put("action_subdata", actionSubData);
    }

    if (feedbackStatus != null) {
      map.put("feedback_status", feedbackStatus);
    }

    if (feedbackComment != null) {
      map.put("feedback_comment", feedbackComment);
    }

    if (feedbackStatusUpdatedAt != null) {
      map.put("feedback_status_updated_at", feedbackStatusUpdatedAt);
    }

    return map;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) {
      json.put("id", id.toString());
    }

    if (userId != null) {
      json.put("userId", userId.toString());
    }

    if (assetId != null) {
      json.put("assetId", assetId.toString());
    }

    if (assetType != null) {
      json.put("assetType", assetType);
    }

    if (entityRating != null) {
      json.put("entityRating", entityRating);
    }

    if (actionSubType != null) {
      json.put("actionSubtype", actionSubType);
    }

    if (actionSubData != null) {
      json.put("actionSubdata", actionSubData);
    }

    if (ratingCreatedAt != null) {
      json.put("ratingCreatedAt", ratingCreatedAt.toString());
    }

    if (ratingUpdatedAt != null) {
      json.put("ratingUpdatedAt", ratingUpdatedAt.toString());
    }

    if (feedbackStatus != null) {
      json.put("feedbackStatus", feedbackStatus);
    }

    if (feedbackComment != null) {
      json.put("feedbackComment", feedbackComment);
    }

    if (feedbackStatusUpdatedAt != null) {
      json.put("feedbackStatusUpdatedAt", feedbackStatusUpdatedAt.toString());
    }

    return json;
  }

  @Override
  @JsonIgnore
  public String getTableName() {
    return "";
  }
}
