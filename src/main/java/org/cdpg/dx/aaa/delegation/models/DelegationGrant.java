package org.cdpg.dx.aaa.delegation.models;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.*;
import org.cdpg.dx.aaa.delegation.util.Constants;
import org.cdpg.dx.aaa.delegation.util.Status;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

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
          json.getString("delegation_id") != null
              ? UUID.fromString(json.getString("delegation_id"))
              : null,
          requireNonNull(UUID.fromString(json.getString("delegator_id")), "delegator_id"),
          requireNonNull(UUID.fromString(json.getString("delegate_id")), "delegate_id"),
          requireNonNull(json.getString("justification"), "justification"),
          parseDateTime(json.getString("expiry_at")),
          effectiveStatus(
              json.getString("status") != null
                  ? json.getString("status")
                  : Status.ACTIVE.getStatus(),
              parseDateTime(json.getString("expiry_at"))),
          parseDateTime(json.getString("created_at")),
          parseDateTime(json.getString("revoked_at")));
    } catch (Exception e) {
      throw new DxValidationException("Invalid or missing field: " + e.getMessage());
    }
  }

  /**
   * The stored status is never moved from {@code active} to {@code expired}, so an active grant
   * whose {@code expiry_at} has passed is reported as {@code expired} when read.
   */
  public static String effectiveStatus(String status, LocalDateTime expiryAt) {
    if (Status.ACTIVE.getStatus().equalsIgnoreCase(status)
        && expiryAt != null
        && !expiryAt.isAfter(LocalDateTime.now())) {
      return Status.EXPIRED.getStatus();
    }
    return status;
  }

  public static DelegationGrant fromRequestJson(JsonObject json) {
    try {
      return new DelegationGrant(
          json.getString("delegationId") != null
              ? UUID.fromString(json.getString("delegationId"))
              : null,

          requireNonNull(
              UUID.fromString(json.getString("delegatorId")),
              "delegatorId"),

          requireNonNull(
              UUID.fromString(json.getString("delegateId")),
              "delegateId"),

          requireNonNull(
              json.getString("justification"),
              "justification"),

          parseDateTime(json.getString("expiryAt")),

          json.getString("status") != null
              ? json.getString("status")
              : Status.ACTIVE.getStatus(),

          parseDateTime(json.getString("createdAt")),

          parseDateTime(json.getString("revokedAt"))
      );
    } catch (Exception e) {
      throw new DxValidationException(
          "Invalid or missing field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (delegationId != null) json.put("delegationId", delegationId.toString());
    json.put("delegatorId", delegatorId.toString());
    json.put("delegateId", delegateId.toString());
    json.put("justification", justification);
    if (expiryAt != null) json.put("expiryAt", expiryAt.format(FORMATTER));
    if (status != null) json.put("status", status);
    if (createdAt != null) json.put("createdAt", createdAt.format(FORMATTER));
    if (revokedAt != null) json.put("revokedAt", revokedAt.format(FORMATTER));
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
    return Constants.DELEGATION_GRANT_TABLE;
  }
}
