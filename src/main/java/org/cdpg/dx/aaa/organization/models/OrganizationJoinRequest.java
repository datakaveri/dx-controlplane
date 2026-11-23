package org.cdpg.dx.aaa.organization.models;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record OrganizationJoinRequest(
    UUID id,
    UUID organizationId,
    UUID userId,
    String userName,
    String status,
    String jobTitle,
    String empId,
    String officialEmail,
    UUID requestedNodeId,
    LocalDateTime requestedAt,
    LocalDateTime processedAt,
    // Role the user asks for in the selected department: USER (member) or
    // DEPT_ADMIN. Never ADMIN -- the org_admin is only created with the org.
    Role requestedRole,
    // Optional free-form details collected by the UI (e.g. a purpose for an
    // Admin request). Stored as JSONB and returned unchanged; never interpreted.
    JsonObject additionalInfo)
    implements BaseEntity<OrganizationJoinRequest> {

  /** Upper bound on the encoded size of {@code additionalInfo}, in characters. */
  public static final int MAX_ADDITIONAL_INFO_LENGTH = 10_000;

  public OrganizationJoinRequest {
    requestedRole = requestedRole == null ? Role.USER : requestedRole;
    if (requestedRole == Role.ADMIN) {
      throw new DxValidationException("departmentRole must be either member or dept_admin");
    }
    additionalInfo = additionalInfo == null || additionalInfo.isEmpty() ? null : additionalInfo;
    if (additionalInfo != null && additionalInfo.encode().length() > MAX_ADDITIONAL_INFO_LENGTH) {
      throw new DxValidationException(
          "additionalInfo must be at most " + MAX_ADDITIONAL_INFO_LENGTH + " characters");
    }
  }

  public boolean requestsDeptAdmin() {
    return requestedRole == Role.DEPT_ADMIN;
  }

  private static Role parseRole(String role) {
    if (role == null || role.isBlank()) {
      return Role.USER;
    }
    try {
      return Role.fromString(role);
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("departmentRole must be either member or dept_admin");
    }
  }

  /** Reads {@code additional_info}: a JSON object, or its text form from a JSONB column. */
  private static JsonObject parseAdditionalInfo(Object raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof JsonObject jsonObject) {
      return jsonObject;
    }
    if (raw instanceof String str) {
      if (str.isBlank()) {
        return null;
      }
      try {
        return new JsonObject(str);
      } catch (RuntimeException e) {
        throw new DxValidationException("additionalInfo must be a JSON object");
      }
    }
    if (raw instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> m = (Map<String, Object>) map;
      return new JsonObject(m);
    }
    throw new DxValidationException("additionalInfo must be a JSON object");
  }

  public static OrganizationJoinRequest fromJson(JsonObject orgJoinRequest) {
    try {
      return new OrganizationJoinRequest(
          orgJoinRequest.getString(Constants.ORG_JOIN_ID) != null
              ? UUID.fromString(orgJoinRequest.getString(Constants.ORG_JOIN_ID))
              : null,
          UUID.fromString(
              requireNonNull(
                  orgJoinRequest.getString(Constants.ORGANIZATION_ID), Constants.ORGANIZATION_ID)),
          UUID.fromString(
              requireNonNull(orgJoinRequest.getString(Constants.USER_ID), Constants.USER_ID)),
          requireNonNull(orgJoinRequest.getString(Constants.USER_NAME), Constants.USER_NAME),
          Optional.ofNullable(orgJoinRequest.getString(Constants.STATUS))
              .orElse(Status.PENDING.getStatus()),
          requireNonNull(orgJoinRequest.getString(Constants.JOB_TITLE), Constants.JOB_TITLE),
          requireNonNull(orgJoinRequest.getString(Constants.EMP_ID), Constants.EMP_ID),
          requireNonNull(
              orgJoinRequest.getString(Constants.OFFICIAL_EMAIL), Constants.OFFICIAL_EMAIL),
          orgJoinRequest.getString(Constants.REQUESTED_NODE_ID) != null
              ? UUID.fromString(orgJoinRequest.getString(Constants.REQUESTED_NODE_ID))
              : null,
          parseDateTime(orgJoinRequest.getString(Constants.REQUESTED_AT)),
          parseDateTime(orgJoinRequest.getString(Constants.PROCESSED_AT)),
          parseRole(orgJoinRequest.getString(Constants.REQUESTED_ROLE)),
          parseAdditionalInfo(orgJoinRequest.getValue(Constants.ADDITIONAL_INFO)));
    } catch (DxValidationException e) {
      throw e;
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put(Constants.ORG_JOIN_ID, id.toString());
    json.put(Constants.ORGANIZATION_ID, organizationId.toString());
    json.put(Constants.USER_ID, userId.toString());
    json.put(Constants.USER_NAME, userName.toString());
    json.put(Constants.STATUS, status);
    json.put(Constants.JOB_TITLE, jobTitle);
    json.put(Constants.EMP_ID, empId);
    json.put(Constants.OFFICIAL_EMAIL, officialEmail);
    if (requestedNodeId != null) json.put(Constants.REQUESTED_NODE_ID, requestedNodeId.toString());
    if (requestedAt != null) json.put(Constants.REQUESTED_AT, requestedAt.format(FORMATTER));
    if (processedAt != null) json.put(Constants.PROCESSED_AT, processedAt.format(FORMATTER));
    json.put(Constants.REQUESTED_ROLE, requestedRole.getRoleName());
    if (additionalInfo != null) json.put(Constants.ADDITIONAL_INFO, additionalInfo.copy());

    return json;
  }

  public JsonObject toJsonForUsers() {
    JsonObject json = new JsonObject();

    if (id != null) json.put(Constants.ORG_JOIN_ID, id.toString());
    json.put("organisationId", organizationId.toString());
    json.put("userId", userId.toString());
    json.put("userName", userName.toString());
    json.put("status", status);
    json.put("jobTitle", jobTitle);
    json.put("empId", empId);
    json.put("officialEmail", officialEmail);
    if (requestedNodeId != null) json.put("requestedNodeId", requestedNodeId.toString());
    if (requestedAt != null) json.put("requestedAt", requestedAt.format(FORMATTER));
    if (processedAt != null) json.put("processedAt", processedAt.format(FORMATTER));
    json.put("departmentRole", requestedRole.getRoleName());
    if (additionalInfo != null) json.put("additionalInfo", additionalInfo.copy());

    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put(Constants.ORG_JOIN_ID, id.toString());
    map.put(Constants.ORGANIZATION_ID, organizationId.toString());
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.USER_NAME, userName.toString());
    if (status != null && !status.isEmpty()) map.put(Constants.STATUS, status);
    if (jobTitle != null && !jobTitle.isEmpty()) map.put(Constants.JOB_TITLE, jobTitle);
    if (empId != null && !empId.isEmpty()) map.put(Constants.EMP_ID, empId);
    map.put(Constants.OFFICIAL_EMAIL, officialEmail);
    if (requestedNodeId != null) map.put(Constants.REQUESTED_NODE_ID, requestedNodeId.toString());
    if (requestedAt != null) map.put(Constants.REQUESTED_AT, requestedAt.format(FORMATTER));
    if (processedAt != null) map.put(Constants.PROCESSED_AT, processedAt.format(FORMATTER));
    map.put(Constants.REQUESTED_ROLE, requestedRole.getRoleName());
    if (additionalInfo != null) map.put(Constants.ADDITIONAL_INFO, additionalInfo.copy());

    return map;
  }

  @Override
  public UUID organizationId() {
    return organizationId;
  }

  @Override
  public UUID userId() {
    return userId;
  }

  @Override
  public String getTableName() {
    return Constants.ORG_JOIN_REQUEST_TABLE;
  }
}
