package org.cdpg.dx.aaa.delegation.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.util.DelegationEntityType;
import org.cdpg.dx.aaa.delegation.util.DelegationRole;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record DelegationScopeConstraint(
  UUID id,
  UUID delegationId,                    // NOT NULL
  DelegationRole role,                  // NOT NULL
  String scope,                         // nullable ('*' allowed)
  UUID entityId,                        // nullable
  DelegationEntityType entityType,      // nullable
  LocalDateTime expiryAt                // NOT NULL
) implements BaseEntity<DelegationScopeConstraint> {

  // -------------------------------------------------------------------------
  // FROM JSON (API → DOMAIN)
  // -------------------------------------------------------------------------
  public static DelegationScopeConstraint fromJson(JsonObject json) {
    try {
      UUID delegationId = requireNonNull(
        json.getString("delegation_id") != null
          ? UUID.fromString(json.getString("delegation_id"))
          : null,
        "delegation_id"
      );

      DelegationRole role = requireNonNull(
        DelegationRole.fromString(json.getString("role")),
        "role"
      );

      LocalDateTime expiryAt = requireNonNull(
        parseDateTime(json.getString("expiry_at")),
        "expiry_at"
      );

      UUID entityId =
        json.getString("entity_id") != null
          ? UUID.fromString(json.getString("entity_id"))
          : null;

      DelegationEntityType entityType =
        json.getString("entity_type") != null
          ? DelegationEntityType.fromString(json.getString("entity_type"))
          : null;

      // ---- Domain validation ----
      if (entityId != null ^ entityType != null) {
        throw new DxValidationException(
          "entity_id and entity_type must be provided together"
        );
      }

      return new DelegationScopeConstraint(
        json.getString("id") != null
          ? UUID.fromString(json.getString("id"))
          : null,
        delegationId,
        role,
        json.getString("scope"),
        entityId,
        entityType,
        expiryAt
      );

    } catch (DxValidationException e) {
      throw e;
    } catch (Exception e) {
      throw new DxValidationException(
        "Invalid delegation scope constraint: " + e.getMessage()
      );
    }
  }

  // -------------------------------------------------------------------------
  // TO JSON (DOMAIN → API)
  // -------------------------------------------------------------------------
  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put("id", id.toString());
    if (delegationId != null) json.put("delegation_id", delegationId.toString());
    if (role != null) json.put("role", role.getRole());
    if (scope != null) json.put("scope", scope);
    if (entityId != null) json.put("entity_id", entityId.toString());
    if (entityType != null) json.put("entity_type", entityType.getType());
    if (expiryAt != null) json.put("expiry_at", expiryAt.format(FORMATTER));

    return json;
  }

  // -------------------------------------------------------------------------
  // TO DB MAP (DOMAIN → DATABASE)
  // -------------------------------------------------------------------------
  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put("id", id);
    if (delegationId != null) map.put("delegation_id", delegationId.toString());
    if (role != null) map.put("role", role.getRole());
    if (scope != null) map.put("scope", scope);
    if (entityId != null) map.put("entity_id", entityId.toString());
    if (entityType != null) map.put("entity_type", entityType.getType());
    if (expiryAt != null) map.put("expiry_at", expiryAt.format(FORMATTER));

    return map;
  }

  @Override
  public String getTableName() {
    return "delegation_scope_constraints";
  }
}
