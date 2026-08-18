package org.cdpg.dx.aaa.appCredentials.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.cdpg.dx.database.postgres.base.entity.BaseEntity;
import org.cdpg.dx.database.postgres.util.EntityUtil;

import static org.cdpg.dx.aaa.appCredentials.util.Constants.*;

public record AppCredentials(
  UUID appId,
  UUID userId,
  String appSecret,
  String expiryAt,
  String status,
  String role,
  String createdAt,
  String modifiedAt,
  String revokedAt
) implements BaseEntity<AppCredentials> {

  public static AppCredentials fromJson(JsonObject json) {
    return new AppCredentials(
      json.getString(APP_ID) != null
        ? UUID.fromString(json.getString(APP_ID))
        : null,

      json.getString(USER_ID) != null
        ? UUID.fromString(json.getString(USER_ID))
        : null,

      json.getString(APP_SECRET),
      json.getString(EXPIRY_AT),
      json.getString(STATUS),
      json.getString(ROLE),
      json.getString(CREATED_AT),
      json.getString(MODIFIED_AT),
      json.getString(REVOKED_AT)
    );
  }

  /* ---------- ENTITY → DB MAP ---------- */
  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (appId != null) {
      map.put(APP_ID, appId.toString());
    }

    if (userId != null) {
      map.put(USER_ID, userId.toString());
    }

    EntityUtil.putIfNonEmpty(map, APP_SECRET, appSecret);
    EntityUtil.putIfNonEmpty(map, EXPIRY_AT, expiryAt);
    EntityUtil.putIfNonEmpty(map, ROLE, role);
    EntityUtil.putIfNonEmpty(map, STATUS, status);
    EntityUtil.putIfNonEmpty(map, REVOKED_AT, revokedAt);
    EntityUtil.putIfNonEmpty(map, MODIFIED_AT, modifiedAt);

    return map;
  }


  /* ---------- ENTITY → JSON ---------- */
  @Override
  public JsonObject toJson() {
    return new JsonObject()
      .put(APP_ID, appId != null ? appId.toString() : null)
      .put(USER_ID, userId != null ? userId.toString() : null)
      .put(APP_SECRET, appSecret)
      .put(EXPIRY_AT, expiryAt)
      .put(STATUS, status)
      .put(ROLE, role)
      .put(CREATED_AT, createdAt)
      .put(MODIFIED_AT, modifiedAt)
      .put(REVOKED_AT, revokedAt);
  }

  @Override
  @JsonIgnore
  public String getTableName() {
    return "app_credentials";
  }
}
