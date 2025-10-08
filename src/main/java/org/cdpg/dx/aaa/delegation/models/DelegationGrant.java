package org.cdpg.dx.aaa.delegation.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.aaa.delegation.util.Status.APPROVED;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record DelegationGrant(
  UUID delegationId,
  UUID delegatorId,
  UUID delegateId,
  String justification,
  LocalDateTime expiryAt,
  String status,
  LocalDateTime createdAt,
  LocalDateTime revokedAt
) implements BaseEntity<DelegationGrant> {

  public static DelegationGrant fromJson(JsonObject json) {
    try {
      return new DelegationGrant(
        json.getString("delegation_id") != null ? UUID.fromString(json.getString("delegation_id")) : null,
        requireNonNull(UUID.fromString(json.getString("delegator_id")), "delegator_id"),
        requireNonNull(UUID.fromString(json.getString("delegate_id")), "delegate_id"),
        requireNonNull(json.getString("justification"), "justification"),
        parseDateTime(json.getString("expiry_at")),
        json.getString("status")!=null ? json.getString("status") : APPROVED.getStatus(),
        parseDateTime(json.getString("created_at")),
        parseDateTime(json.getString("revoked_at"))
      );
    } catch (Exception e) {
      throw new DxValidationException("Invalid or missing field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (delegationId != null) json.put("delegation_id", delegationId.toString());
    json.put("delegator_id", delegatorId.toString());
    json.put("delegate_id", delegateId.toString());
    json.put("justification", justification);
    if (expiryAt != null) json.put("expiry_at", expiryAt.format(FORMATTER));
    if (status != null) json.put("status", status);
    if (createdAt != null) json.put("created_at", createdAt.format(FORMATTER));
    if (revokedAt != null) json.put("revoked_at", revokedAt.format(FORMATTER));
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (delegationId != null) map.put("delegation_id", delegationId.toString());
    if (delegatorId != null) map.put("delegator_id", delegatorId.toString());
    if (delegateId != null) map.put("delegate_id", delegateId.toString());
    if (justification != null && !justification.isEmpty()) map.put("justification", justification);
    if (expiryAt != null) map.put("expiry_at", expiryAt.format(FORMATTER));
    if (status != null && !status.isEmpty()) map.put("status", status);
    if (createdAt != null) map.put("created_at", createdAt.format(FORMATTER));
    if (revokedAt != null) map.put("revoked_at", revokedAt.format(FORMATTER));
    return map;
  }

  @Override
  public String getTableName() {
    return "delegation_grants";
  }
}
