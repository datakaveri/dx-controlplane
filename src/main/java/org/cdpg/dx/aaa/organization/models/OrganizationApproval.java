package org.cdpg.dx.aaa.organization.models;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record OrganizationApproval(
    UUID id,
    UUID organizationId,
    UUID requestId,
    OrganizationApprovalType approvalType,
    int requiredLevel,
    UUID processedByUserId,
    UUID processedByNodeId,
    int processedByLevel,
    OrganizationApprovalStage status,
    String remarks,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) implements BaseEntity<OrganizationApproval> {

  public static final String ID = "id";
  public static final String ORGANIZATION_ID = "organization_id";
  public static final String REQUEST_ID = "request_id";
  public static final String APPROVAL_TYPE = "approval_type";
  public static final String REQUIRED_LEVEL = "required_level";
  public static final String PROCESSED_BY_USER_ID = "processed_by_user_id";
  public static final String PROCESSED_BY_NODE_ID = "processed_by_node_id";
  public static final String PROCESSED_BY_LEVEL = "processed_by_level";
  public static final String STATUS = "status";
  public static final String REMARKS = "remarks";
  public static final String CREATED_AT = "created_at";
  public static final String UPDATED_AT = "updated_at";

  public static OrganizationApproval fromJson(JsonObject json) {
    String statusValue = json.getString(STATUS);

    return new OrganizationApproval(
        json.getString(ID) != null
            ? UUID.fromString(json.getString(ID))
            : null,
        UUID.fromString(json.getString(ORGANIZATION_ID)),
        UUID.fromString(json.getString(REQUEST_ID)),
        OrganizationApprovalType.valueOf(
            json.getString(APPROVAL_TYPE).toUpperCase()),
        json.getInteger(REQUIRED_LEVEL),
        json.getString(PROCESSED_BY_USER_ID) != null
            ? UUID.fromString(json.getString(PROCESSED_BY_USER_ID))
            : null,
        json.getString(PROCESSED_BY_NODE_ID) != null
            ? UUID.fromString(json.getString(PROCESSED_BY_NODE_ID))
            : null,
        json.getInteger(PROCESSED_BY_LEVEL, 0),
        statusValue != null
            ? OrganizationApprovalStage.valueOf(statusValue.toUpperCase())
            : null,
        json.getString(REMARKS),
        json.getString(CREATED_AT) != null
            ? LocalDateTime.parse(json.getString(CREATED_AT))
            : null,
        json.getString(UPDATED_AT) != null
            ? LocalDateTime.parse(json.getString(UPDATED_AT))
            : null);
  }

  @Override
  public JsonObject toJson() {
    return new JsonObject()
        .put(ID, id != null ? id.toString() : null)
        .put(ORGANIZATION_ID, organizationId != null ? organizationId.toString() : null)
        .put(REQUEST_ID, requestId != null ? requestId.toString() : null)
        .put(APPROVAL_TYPE, approvalType != null ? approvalType.name() : null)
        .put(REQUIRED_LEVEL, requiredLevel)
        .put(
            PROCESSED_BY_USER_ID,
            processedByUserId != null ? processedByUserId.toString() : null)
        .put(
            PROCESSED_BY_NODE_ID,
            processedByNodeId != null ? processedByNodeId.toString() : null)
        .put(PROCESSED_BY_LEVEL, processedByLevel)
        .put(STATUS, status != null ? status.name().toLowerCase() : null)
        .put(REMARKS, remarks)
        .put(CREATED_AT, createdAt != null ? createdAt.toString() : null)
        .put(UPDATED_AT, updatedAt != null ? updatedAt.toString() : null);
  }

  @Override
  @JsonIgnore
  public String getTableName() {
    return null;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put(ID, id.toString());
    if (organizationId != null) map.put(ORGANIZATION_ID, organizationId.toString());
    if (requestId != null) map.put(REQUEST_ID, requestId.toString());
    if (approvalType != null) map.put(APPROVAL_TYPE, approvalType.name().toLowerCase());

    map.put(REQUIRED_LEVEL, requiredLevel);

    if (processedByUserId != null) {
      map.put(PROCESSED_BY_USER_ID, processedByUserId.toString());
    }

    if (processedByNodeId != null) {
      map.put(PROCESSED_BY_NODE_ID, processedByNodeId.toString());
    }

    map.put(PROCESSED_BY_LEVEL, processedByLevel);

    if (status != null) {
      map.put(STATUS, status.name().toLowerCase());
    }

    if (remarks != null) {
      map.put(REMARKS, remarks);
    }

    if (createdAt != null) map.put(CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) map.put(UPDATED_AT, updatedAt.format(FORMATTER));

    return map;
  }
}