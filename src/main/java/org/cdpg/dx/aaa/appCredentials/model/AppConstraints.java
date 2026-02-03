package org.cdpg.dx.aaa.appCredentials.model;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.core.util.UuidUtil;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;
import org.cdpg.dx.database.postgres.util.EntityUtil;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.aaa.appCredentials.util.Constants.*;

public record AppConstraints(
  UUID id,
  UUID appId,
  String scope,
  String entityId,
  String entityType,
  UUID userId
) implements BaseEntity<AppConstraints>
{

    public static AppConstraints fromJson(JsonObject json) {
    return new AppConstraints(
      json.getString(ID) != null
        ? UUID.fromString(json.getString(ID))
        : null,
      json.getString(APP_ID) != null
        ? UUID.fromString(json.getString(APP_ID))
        : null,
      json.getString(SCOPE)!= null
        ? json.getString(SCOPE)
        : "*",
      json.getString(ENTITY_ID) != null
        ? json.getString(ENTITY_ID)
        : "*",
      json.getString(ENTITY_TYPE) != null
        ? json.getString(ENTITY_TYPE)
        : "*",
      UUID.fromString(json.getString(USER_ID)));

  }

    /* ---------- ENTITY → DB MAP ---------- */
    @Override
    public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (appId != null) {
      map.put(APP_ID, appId.toString());
    }

    if (id != null) {
      map.put(ID, id.toString());
    }

      if (scope != null) {
        map.put(SCOPE, scope);
      }

      if (entityId != null) {
        map.put(ENTITY_ID, entityId.toString());
      }

      if (entityType != null) {
        map.put(ENTITY_TYPE,entityType);
      }

      if (userId != null) {
        map.put(USER_ID,userId.toString());
      }

    return map;
  }


    /* ---------- ENTITY → JSON ---------- */
    @Override
    public JsonObject toJson() {
    return new JsonObject()
      .put(APP_ID, appId != null ? appId.toString() : null)
      .put(ID, id != null ? id.toString() : null)
      .put(SCOPE, scope != null ? scope : "*")
      .put(ENTITY_ID, entityId != null ? entityId.toString() : "*")
      .put(ENTITY_TYPE, entityType != null ? entityType : "*")
      .put(USER_ID, userId.toString());
    }

    @Override
    public String getTableName() {
    return "app_constraints";
  }

}
