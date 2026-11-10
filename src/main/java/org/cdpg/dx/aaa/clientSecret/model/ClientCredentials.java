package org.cdpg.dx.aaa.clientSecret.model;

import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;
import org.cdpg.dx.database.postgres.util.EntityUtil;

public record ClientCredentials(
    UUID userId, String clientId, String clientSecret, String createdAt, String updatedAt)
    implements BaseEntity<ClientCredentials> {

  public static ClientCredentials fromJson(JsonObject json) {
    UUID userId =
        json.getString("user_id") != null ? UUID.fromString(json.getString("user_id")) : null;
    String clientId = json.getString("client_id") != null ? json.getString("client_id") : null;
    String clientSecret = json.getString("client_secret");
    String createdAt = json.getString("created_at");
    String updatedAt = json.getString("updated_at");
    return new ClientCredentials(userId, clientId, clientSecret, createdAt, updatedAt);
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    EntityUtil.putIfNonEmpty(map, "user_id", userId.toString());
    EntityUtil.putIfNonEmpty(map, "client_id", clientId);
    EntityUtil.putIfNonEmpty(map, "client_secret", clientSecret);

    return map;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("user_id", userId != null ? userId.toString() : null);
    json.put("client_id", clientId);
    json.put("client_secret", clientSecret);
    json.put("created_at", createdAt);
    json.put("updated_at", updatedAt);
    return json;
  }

  @Override
  public String getTableName() {
    return null;
  }
}
