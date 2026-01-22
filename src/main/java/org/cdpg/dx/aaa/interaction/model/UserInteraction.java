package org.cdpg.dx.aaa.interaction.model;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.cdpg.dx.aaa.interaction.enums.ActionType;
import org.cdpg.dx.aaa.interaction.enums.InteractionValue;
import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record UserInteraction(
    UUID id,
    UUID userId,
    UUID entityId,
    EntityType entityType,
    ActionType actionType,
    InteractionValue value)
    implements BaseEntity<UserInteraction> {

  public static UserInteraction fromJson(JsonObject json) {
    return new UserInteraction(
        json.getString("id") != null ? UUID.fromString(json.getString("id")) : null,
        UUID.fromString(json.getString("user_id")),
        UUID.fromString(json.getString("entity_id")),
        EntityType.valueOf(json.getString("entity_type")),
        ActionType.valueOf(json.getString("action_type")),
        InteractionValue.valueOf(json.getString("value")));
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    map.put("user_id", userId.toString());
    map.put("entity_id", entityId.toString());
    map.put("entity_type", entityType.name());
    map.put("action_type", actionType.name());
    map.put("value", value.name());

    return map;
  }

  @Override
  public JsonObject toJson() {
    return new JsonObject()
        .put("user_id", userId.toString())
        .put("entity_id", entityId.toString())
        .put("entity_type", entityType.name())
        .put("action_type", actionType.name())
        .put("value", value.name());
  }

  @Override
  public String getTableName() {
    return "";
  }
}
