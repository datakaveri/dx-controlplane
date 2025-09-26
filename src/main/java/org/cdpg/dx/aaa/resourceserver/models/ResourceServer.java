package org.cdpg.dx.aaa.resourceserver.models;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.resourceserver.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.aaa.resourceserver.config.Constants.ROLES;
import static org.cdpg.dx.auth.authorization.model.DxRole.COS_ADMIN;
import static org.cdpg.dx.auth.authorization.model.DxRole.ORG_ADMIN;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;


public record ResourceServer(
  UUID id,
  String name,
  String url,
  String type,
  UUID ownerId,
  String visibility,
  String status,
  JsonArray accessType,
  String injectionType,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) implements BaseEntity<ResourceServer> {

  public static ResourceServer fromJson(JsonObject json) {
      LOGGER.debug("Inside fromJson of ResourceServer {}", json);
    try {
        return new ResourceServer(
        json.getString(Constants.ID) != null ? UUID.fromString(json.getString(Constants.ID)) : UUID.randomUUID(),
        requireNonNull(json.getString(Constants.NAME), Constants.NAME),
        requireNonNull(json.getString(Constants.URL), Constants.URL),
        requireNonNull(json.getString(Constants.TYPE), Constants.TYPE),
        json.getString(Constants.OWNER_ID) != null ? UUID.fromString(json.getString(Constants.OWNER_ID)) : null,
        requireNonNull(json.getString(Constants.VISIBILITY), Constants.VISIBILITY),
        json.getString(Constants.STATUS)!=null?json.getString(Constants.STATUS):getStatusFromJson(json),
        json.getJsonArray(Constants.ACCESS_TYPE),
        json.getString(Constants.INJECTION_TYPE),
        json.getString(Constants.CREATED_AT) != null ? LocalDateTime.parse(json.getString(Constants.CREATED_AT), FORMATTER) : null,
        json.getString(Constants.UPDATED_AT) != null ? LocalDateTime.parse(json.getString(Constants.UPDATED_AT), FORMATTER) : null
      );
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }
    private static final Logger LOGGER = LogManager.getLogger(ResourceServer.class);

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (id != null) json.put(Constants.ID, id.toString());
    json.put(Constants.NAME, name);
    json.put(Constants.URL, url);
    json.put(Constants.TYPE, type);
    json.put(Constants.OWNER_ID, ownerId.toString());
    json.put(Constants.VISIBILITY, visibility);
    json.put(Constants.STATUS, status);
    json.put(Constants.ACCESS_TYPE, accessType);
    json.put(Constants.INJECTION_TYPE, injectionType);
    json.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    json.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (id != null) map.put(Constants.ID, id.toString());
    if (name != null && !name.isEmpty()) map.put(Constants.NAME, name);
    if (url != null && !url.isEmpty()) map.put(Constants.URL, url);
    if (type != null && !type.isEmpty()) map.put(Constants.TYPE, type);
    if (ownerId != null) map.put(Constants.OWNER_ID, ownerId.toString());
    if (visibility != null && !visibility.isEmpty()) map.put(Constants.VISIBILITY, visibility);
    if (status != null && !status.isEmpty()) map.put(Constants.STATUS, status);
    if (accessType != null && !accessType.isEmpty()) map.put(Constants.ACCESS_TYPE, accessType);
    if (injectionType != null && !injectionType.isEmpty()) map.put(Constants.INJECTION_TYPE, injectionType);
    if (createdAt != null) map.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));
    return map;
  }
    private static String getStatusFromJson(JsonObject json) {
        JsonArray roles = json.getJsonArray(ROLES);
        LOGGER.debug("Roles in getStatusFromJson: " + roles.contains("org_admin"));
        if (roles == null || roles.isEmpty()) {
            throw new DxValidationException("Missing or invalid required field: roles");
        }

        if (roles.contains(ORG_ADMIN.getRole())) {
            LOGGER.debug("Role is org_admin");
            return "PENDING";
        } else if (roles.contains(COS_ADMIN.getRole())) {
            LOGGER.debug("Role is cos_admin");
            return "ACTIVE";
        } else {
            throw new DxValidationException("Invalid role: must contain either ORG_ADMIN or COS_ADMIN");
        }
    }


  @Override
  public String getTableName() {
    return Constants.RESOURCE_SERVER_TABLE;
  }
}


