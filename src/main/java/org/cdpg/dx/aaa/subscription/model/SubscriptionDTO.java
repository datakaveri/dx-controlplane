package org.cdpg.dx.aaa.subscription.model;

import static org.cdpg.dx.aaa.subscription.util.SubscriptionConstants.SUBSCRIPTION_TABLE;

import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record SubscriptionDTO(
    UUID id,
    String queue_name,
    String entityId,
    LocalDateTime expiryAt,
    String userid,
    String provider_id,
    String delegator_id,
    Optional<LocalDateTime> created_at,
    Optional<LocalDateTime> updated_at)
    implements BaseEntity<SubscriptionDTO> {

  public static SubscriptionDTO fromJson(JsonObject json) {
    return new SubscriptionDTO(
        UUID.fromString(json.getString("id")),
        json.getString("queue_name"),
        json.getString("entityId"),
        LocalDateTime.parse(json.getString("expiryAt")),
        json.getString("userid"),
        json.getString("provider_id"),
        json.getString("delegator_id"),
        Optional.of(LocalDateTime.parse(json.getString("created_at"))),
        Optional.of(LocalDateTime.parse(json.getString("updated_at"))));
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (id != null) map.put("id", id.toString());
    if (queue_name != null) map.put("queue_name", queue_name);
    if (entityId != null) map.put("entityId", entityId);
    if (expiryAt != null) map.put("expiryAt", expiryAt.toString());
    if (userid != null) map.put("user_id", userid);
    if (provider_id != null) map.put("provider_id", provider_id);
    if (delegator_id != null) map.put("delegator_id", delegator_id);
    return map;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (id != null) json.put("id", id.toString());
    if (queue_name != null) json.put("queue_name", queue_name);
    if (entityId != null) json.put("entityId", entityId);
    if (expiryAt != null) json.put("expiryAt", expiryAt);
    if (userid != null) json.put("userid", userid);
    if (provider_id != null) json.put("provider_id", provider_id);
    if (delegator_id != null) json.put("delegator_id", delegator_id);
    created_at.ifPresent(v -> json.put("created_at", v.toString()));
    updated_at.ifPresent(v -> json.put("updated_at", v.toString()));
    return json;
  }

  @Override
  public String getTableName() {
    return SUBSCRIPTION_TABLE;
  }
}
