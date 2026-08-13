package org.cdpg.dx.aaa.interaction.v2.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record UserFeedback(
  UUID id,
  UUID userId,
  UUID assetId,
  String assetType,
  Integer entityRating,
  String actionSubType,
  JsonObject actionSubData)
  implements BaseEntity<UserFeedback> {

  public static UserFeedback fromJson(JsonObject json) {

    return new UserFeedback(
      json.getString("id") != null
        ? UUID.fromString(json.getString("id"))
        : null,

      json.getString("user_id") != null
        ? UUID.fromString(json.getString("user_id"))
        : null,

      json.getString("asset_id") != null
        ? UUID.fromString(json.getString("asset_id"))
        : null,

        json.getString("asset_type") != null
            ? json.getString("asset_type")
            : null,

      json.getInteger("entity_rating") != null
        ? json.getInteger("entity_rating")
        : null,

      json.getString("action_subtype") != null
        ? json.getString("action_subtype")
        : null,

        // nullable
      json.getJsonObject("action_subdata") != null
        ? json.getJsonObject("action_subdata")
        : null // nullable
    );
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {

    Map<String, Object> map = new HashMap<>();

    if (userId != null)
      map.put("user_id", userId.toString());

    if (assetId != null)
      map.put("asset_id", assetId.toString());

    if (assetType != null)
      map.put("asset_type", assetType);

    if (entityRating != null)
      map.put("entity_rating", entityRating);

    if (actionSubType != null)
      map.put("action_subtype", actionSubType);

    if (actionSubData!= null)
      map.put("action_subdata", actionSubData);

    return map;
  }

  public static UserFeedback fromRequestJson(JsonObject json) {

    return new UserFeedback(
        json.getString("id") != null
            ? UUID.fromString(json.getString("id"))
            : null,

        json.getString("userId") != null
            ? UUID.fromString(json.getString("userId"))
            : null,

        json.getString("assetId") != null
            ? UUID.fromString(json.getString("assetId"))
            : null,

        json.getString("assetType") != null
            ?json.getString("assetType")
            : null,

        json.getInteger("entityRating") != null
            ? json.getInteger("entityRating")
            : null,

        json.getString("actionSubtype") != null
            ? json.getString("actionSubtype")
            : null,

        json.getJsonObject("actionSubdata") != null
            ? json.getJsonObject("actionSubdata")
            : null);
  }

  @Override
  public JsonObject toJson() {

    JsonObject json = new JsonObject();

    if (id != null)
      json.put("id", id.toString());

    if (userId != null)
      json.put("userId", userId.toString());

    if (assetId != null)
      json.put("assetId", assetId.toString());

    if (assetType != null)
      json.put("assetType", assetType);

    if (entityRating != null)
      json.put("entityRating", entityRating);

    if (actionSubType != null)
      json.put("actionSubtype", actionSubType);

    if (actionSubData != null)
      json.put("actionSubdata", actionSubData);

    return json;
  }

  @Override
  @JsonIgnore
  public String getTableName() {
    return "";
  }
}
