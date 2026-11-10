package org.cdpg.dx.aaa.vote.model;

import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.cdpg.dx.aaa.vote.model.VoteType;
import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record ItemVote(
    UUID id,
    UUID userId,
    UUID entityId,
    EntityType entityType,
    VoteType voteType,
    String createdAt,
    String updatedAt)
    implements BaseEntity<ItemVote> {

  public static ItemVote fromJson(JsonObject json) {
    return new ItemVote(
        json.getString("id") != null ? UUID.fromString(json.getString("id")) : null,
        UUID.fromString(json.getString("user_id")),
        UUID.fromString(json.getString("entity_id")),
        EntityType.valueOf(json.getString("entity_type")),
        VoteType.valueOf(json.getString("vote_type")),
        json.getString("created_at"),
        json.getString("updated_at"));
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put("id", id.toString());
    json.put("user_id", userId.toString());
    json.put("entity_id", entityId.toString());
    json.put("entity_type", entityType.name());
    json.put("vote_type", voteType.name());

    if (createdAt != null) json.put("created_at", createdAt);
    if (updatedAt != null) json.put("updated_at", updatedAt);

    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    map.put("user_id", userId.toString());
    map.put("entity_id", entityId.toString());
    map.put("entity_type", entityType.name());
    map.put("vote_type", voteType.name());

    return map;
  }

  @Override
  public String getTableName() {
    return "";
  }
}
