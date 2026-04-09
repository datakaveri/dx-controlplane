package org.cdpg.dx.aaa.delegation.models;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.util.DelegationEntityType;
import org.cdpg.dx.aaa.delegation.util.DelegationRole;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record DelegationScopeConstraint(
  UUID id,
  UUID delegationId,                    // NOT NULL
  DelegationRole role,                  // NOT NULL
  String scope,                         // NOT NULL ('*' allowed)
  String entityId,                        // NOT NULL ('*' allowed)
  String entityType,      // NOT NULL ('*' allowed)
  LocalDateTime expiryAt                // NOT NULL
) implements BaseEntity<DelegationScopeConstraint> {
  private static final Logger LOGGER = LoggerFactory.getLogger(DelegationScopeConstraint.class);

  // -------------------------------------------------------------------------
  // FROM JSON (API → DOMAIN)
  // -------------------------------------------------------------------------
  public static DelegationScopeConstraint fromJson(JsonObject json) {
    LOGGER.debug("Parsing delegation scope constraint from JSON: {}", json);

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


      String entityId = json.getString("entity_id")!=null
          ? json.getString("entity_id")
          : null;

      String entityType =
        json.getString("entity_type") != null
          ? json.getString("entity_type")
          : null;


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
      LOGGER.error("Failed to parse delegation scope constraint", e);
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
    if (entityId != null) json.put("entity_id", entityId);
    if (entityType != null) json.put("entity_type", entityType);
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
    if (entityId != null) map.put("entity_id", entityId);
    if (entityType != null) map.put("entity_type", entityType);
    if (expiryAt != null) map.put("expiry_at", expiryAt.format(FORMATTER));

    return map;
  }

  @Override
  public String getTableName() {
    return "delegation_scope_constraints";
  }
}
