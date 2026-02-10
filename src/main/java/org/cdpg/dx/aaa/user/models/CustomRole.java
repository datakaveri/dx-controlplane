package org.cdpg.dx.aaa.user.models;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.aaa.user.util.constants.*;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record CustomRole(
  UUID id,
  UUID userId,
//  String role,
  JsonArray scope,
  UUID requestedBy,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) implements BaseEntity<CustomRole> {

  public static CustomRole fromJson(JsonObject json) {
    try {
      return new CustomRole(
        json.getString(CUSTOM_ROLE_ID) != null
          ? UUID.fromString(json.getString(CUSTOM_ROLE_ID))
          : null,
        UUID.fromString(requireNonNull(json.getString(USER_ID), USER_ID)),
//        requireNonNull(json.getString(ROLE), ROLE),
        json.getJsonArray(SCOPE), // nullable
        UUID.fromString(json.getString(REQUESTED_BY)),
        parseDateTime(json.getString(CREATED_AT)),
        parseDateTime(json.getString(UPDATED_AT))
      );
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put(CUSTOM_ROLE_ID, id.toString());
    json.put(USER_ID, userId.toString());
//    json.put(ROLE, role);

    if (scope != null && !scope.isEmpty()) {
      json.put(SCOPE, scope);
    }

    json.put(REQUESTED_BY,requestedBy.toString());

    if (createdAt != null) json.put(CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) json.put(UPDATED_AT, updatedAt.format(FORMATTER));

    return json;
  }

  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put(CUSTOM_ROLE_ID, id);
    map.put(USER_ID, userId.toString());
//    if (!role.isEmpty()) map.put(ROLE, role);

    if (scope != null && !scope.isEmpty()) {
      map.put(SCOPE, scope);
    }

    map.put(REQUESTED_BY,requestedBy.toString());

    if (createdAt != null) map.put(CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) map.put(UPDATED_AT, updatedAt.format(FORMATTER));

    return map;
  }

  @Override
  public String getTableName() {
    return CUSTOM_ROLE_SCOPE_TABLE;
  }
}
