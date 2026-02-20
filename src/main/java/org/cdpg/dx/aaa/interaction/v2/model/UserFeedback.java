package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record UserFeedback(
  UUID id,
  UUID userId,
  UUID assetId,
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

    if (entityRating != null)
      map.put("entity_rating", entityRating);

    if (actionSubType != null)
      map.put("action_subtype", actionSubType);

    if (actionSubData!= null)
      map.put("action_subdata", actionSubData);

    return map;
  }

  @Override
  public JsonObject toJson() {

    JsonObject json = new JsonObject();

    if (id != null)
      json.put("id", id.toString());

    if (userId != null)
      json.put("user_id", userId.toString());

    if (assetId != null)
      json.put("asset_id", assetId.toString());

    if (entityRating != null)
      json.put("entity_rating", entityRating);

    if (actionSubType != null)
      json.put("action_subtype", actionSubType);

    if (actionSubData != null)
      json.put("action_subdata", actionSubData);

    return json;
  }

  @Override
  public String getTableName() {
    return "";
  }
}
