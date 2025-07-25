package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record OrganizationJoinRequest(
  UUID id,
  UUID organizationId,
  UUID userId,
  String userName,
  String status,
  String jobTitle,
  String empId,
  String officialEmail,
  LocalDateTime requestedAt,
  LocalDateTime processedAt
) implements BaseEntity<OrganizationJoinRequest> {

  public static OrganizationJoinRequest fromJson(JsonObject orgJoinRequest) {
    try {
      return new OrganizationJoinRequest(
        orgJoinRequest.getString(Constants.ORG_JOIN_ID) != null
          ? UUID.fromString(orgJoinRequest.getString(Constants.ORG_JOIN_ID))
          : null,
        UUID.fromString(requireNonNull(orgJoinRequest.getString(Constants.ORGANIZATION_ID), Constants.ORGANIZATION_ID)),
        UUID.fromString(requireNonNull(orgJoinRequest.getString(Constants.USER_ID), Constants.USER_ID)),
        requireNonNull(orgJoinRequest.getString(Constants.USER_NAME),Constants.USER_NAME),
        Optional.ofNullable(orgJoinRequest.getString(Constants.STATUS)).orElse(Status.PENDING.getStatus()),
        requireNonNull(orgJoinRequest.getString(Constants.JOB_TITLE), Constants.JOB_TITLE),
        requireNonNull(orgJoinRequest.getString(Constants.EMP_ID), Constants.EMP_ID),
        requireNonNull(orgJoinRequest.getString(Constants.OFFICIAL_EMAIL), Constants.OFFICIAL_EMAIL),
        parseDateTime(orgJoinRequest.getString(Constants.REQUESTED_AT)),
        parseDateTime(orgJoinRequest.getString(Constants.PROCESSED_AT))
      );
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
    if (requestedAt != null) json.put(Constants.REQUESTED_AT, requestedAt.format(FORMATTER));
    if (processedAt != null) json.put(Constants.PROCESSED_AT, processedAt.format(FORMATTER));

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
    if (requestedAt != null) json.put("requestedAt", requestedAt.format(FORMATTER));
    if (processedAt != null) json.put("processedAt", processedAt.format(FORMATTER));

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
    if (requestedAt != null) map.put(Constants.REQUESTED_AT, requestedAt.format(FORMATTER));
    if (processedAt != null) map.put(Constants.PROCESSED_AT, processedAt.format(FORMATTER));

    return map;
  }

  @Override
  public String getTableName() {
    return Constants.ORG_JOIN_REQUEST_TABLE;
  }
}
