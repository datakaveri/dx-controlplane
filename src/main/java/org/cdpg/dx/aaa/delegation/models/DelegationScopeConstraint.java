package org.cdpg.dx.aaa.delegation.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.util.RoleScopeMapping;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record DelegationScopeConstraint(
  UUID id,
  UUID delegationId,
  String scope,
  UUID entityId,
  LocalDateTime expiryAt
) implements BaseEntity<DelegationScopeConstraint> {

  public static DelegationScopeConstraint fromJson(JsonObject json) {
    try {
      return new DelegationScopeConstraint(
      json.getString("id") != null ? UUID.fromString(json.getString("id")) : null,
      requireNonNull(
        json.getString("delegation_id") != null ? UUID.fromString(json.getString("delegation_id")) : null,
        "delegation_id"
      ),
        requireNonNull(json.getString("scope"), "scope"),
      json.getString("entity_id") != null ? UUID.fromString(json.getString("entity_id")) : null,
      parseDateTime(json.getString("expiry_at"))
      );
    } catch (Exception e) {
      throw new DxValidationException("Invalid or missing field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (id != null) json.put("id", id.toString());
    if (delegationId != null) json.put("delegation_id", delegationId.toString());
    if (scope != null) json.put("scope", scope);
    if (entityId != null) json.put("entity_id", entityId.toString());
    if (expiryAt != null) json.put("expiry_at", expiryAt.format(FORMATTER));
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (id != null) map.put("id", id.toString());
    if (delegationId != null) map.put("delegation_id", delegationId.toString());
    if (scope != null) map.put("scope", scope);
    if (entityId != null) map.put("entity_id", entityId.toString());
    if (expiryAt != null) map.put("expiry_at", expiryAt.format(FORMATTER));
    return map;
  }

  @Override
  public String getTableName() {
    return "delegation_scope_constraints";
  }
}
