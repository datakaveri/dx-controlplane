package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record ProviderRoleRequest(
    UUID id,
    UUID userId,
    UUID orgId, // nullable — null for platform providers
    String providerType, // "org" or "platform"
    String status,
    UUID requestedNodeId, // nullable — which department this provider request is scoped to
    LocalDateTime createdAt,
    LocalDateTime updatedAt)
    implements BaseEntity<ProviderRoleRequest> {

  public static ProviderRoleRequest fromJson(JsonObject json) {
    try {
      String orgIdStr = json.getString("organization_id");
      UUID orgId = (orgIdStr != null && !orgIdStr.isBlank()) ? UUID.fromString(orgIdStr) : null;

      String providerType = json.getString(Constants.PROVIDER_TYPE);
      if (providerType == null || providerType.isBlank()) {
        providerType = Constants.PROVIDER_TYPE_ORG;
      }

      String requestedNodeIdStr = json.getString(Constants.REQUESTED_NODE_ID);
      UUID requestedNodeId =
          (requestedNodeIdStr != null && !requestedNodeIdStr.isBlank())
              ? UUID.fromString(requestedNodeIdStr)
              : null;

      return new ProviderRoleRequest(
          json.getString(Constants.ORG_CREATE_ID) != null
              ? UUID.fromString(json.getString(Constants.ORG_CREATE_ID))
              : null,
          UUID.fromString(requireNonNull(json.getString("user_id"), "user_id")),
          orgId,
          providerType,
          json.getString(org.cdpg.dx.aaa.credit.util.Constants.STATUS) != null
              ? json.getString(org.cdpg.dx.aaa.credit.util.Constants.STATUS)
              : Status.PENDING.getStatus(),
          requestedNodeId,
          parseDateTime(json.getString(Constants.CREATED_AT)),
          parseDateTime(json.getString(Constants.UPDATED_AT)));
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put(Constants.ORG_CREATE_ID, id.toString());
    json.put("user_id", userId.toString());
    if (orgId != null) json.put("organization_id", orgId.toString());
    json.put(Constants.PROVIDER_TYPE, providerType);
    json.put("status", status);
    if (requestedNodeId != null) json.put(Constants.REQUESTED_NODE_ID, requestedNodeId.toString());
    if (createdAt != null) json.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) json.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return json;
  }

  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (id != null) map.put(Constants.ORG_CREATE_ID, id.toString());
    map.put("user_id", userId.toString());
    if (orgId != null) map.put("organization_id", orgId.toString());
    map.put(Constants.PROVIDER_TYPE, providerType);
    map.put("status", status);
    if (requestedNodeId != null) map.put(Constants.REQUESTED_NODE_ID, requestedNodeId.toString());
    if (createdAt != null) map.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return map;
  }

  @Override
  public String getTableName() {
    return "";
  }
}
