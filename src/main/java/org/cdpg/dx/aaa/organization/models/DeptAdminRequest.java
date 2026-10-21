package org.cdpg.dx.aaa.organization.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

/**
 * A member's request to become dept_admin of their own department --
 * approved by the org_admin or any strict ancestor dept_admin above them
 * (see {@code OrganizationApprovalServiceImpl}'s ancestor-or-self rule;
 * strict here because the requester's own, not-yet-admin'd department does
 * not count as containing itself for this request type).
 */
public record DeptAdminRequest(
    UUID id,
    UUID organizationId,
    UUID userId,
    UUID requestedNodeId,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt)
    implements BaseEntity<DeptAdminRequest> {

  public static DeptAdminRequest fromJson(JsonObject json) {
    try {
      return new DeptAdminRequest(
          json.getString("id") != null ? UUID.fromString(json.getString("id")) : null,
          UUID.fromString(
              requireNonNull(
                  json.getString(Constants.ORGANIZATION_ID), Constants.ORGANIZATION_ID)),
          UUID.fromString(requireNonNull(json.getString(Constants.USER_ID), Constants.USER_ID)),
          UUID.fromString(
              requireNonNull(
                  json.getString(Constants.REQUESTED_NODE_ID), Constants.REQUESTED_NODE_ID)),
          json.getString(Constants.STATUS) != null
              ? json.getString(Constants.STATUS)
              : Status.PENDING.getStatus(),
          parseDateTime(json.getString(Constants.CREATED_AT)),
          parseDateTime(json.getString(Constants.UPDATED_AT)));
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put("id", id.toString());
    json.put(Constants.ORGANIZATION_ID, organizationId.toString());
    json.put(Constants.USER_ID, userId.toString());
    json.put(Constants.REQUESTED_NODE_ID, requestedNodeId.toString());
    json.put(Constants.STATUS, status);
    if (createdAt != null) json.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) json.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put("id", id.toString());
    map.put(Constants.ORGANIZATION_ID, organizationId.toString());
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.REQUESTED_NODE_ID, requestedNodeId.toString());
    if (status != null && !status.isEmpty()) map.put(Constants.STATUS, status);
    if (createdAt != null) map.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return map;
  }

  @Override
  @JsonIgnore
  public String getTableName() {
    return Constants.DEPT_ADMIN_REQUEST_TABLE;
  }
}
