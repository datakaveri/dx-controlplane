package org.cdpg.dx.aaa.credit.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.util.Constants;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record ComputeRole(
  UUID id,
  UUID userId,
  String userName,
  String status,
  UUID approvedBy,
  JsonObject additionalInfo,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) implements BaseEntity<ComputeRole> {

  public static ComputeRole fromJson(JsonObject json) {
    try {
      return new ComputeRole(
        json.containsKey(Constants.COMPUTE_ROLE_ID)
          ? UUID.fromString(json.getString(Constants.COMPUTE_ROLE_ID))
          : null,
        UUID.fromString(requireNonNull(json.getString(Constants.USER_ID), Constants.USER_ID)),
        requireNonNull(json.getString(Constants.USER_NAME),Constants.USER_NAME),
        json.getString(Constants.STATUS) != null
          ? json.getString(Constants.STATUS)
          : Status.PENDING.getStatus(),
        json.getString(Constants.APPROVED_BY) != null
          ? UUID.fromString(json.getString(Constants.APPROVED_BY))
          : null,
        json.getJsonObject(Constants.ADDITONAL_INFO),
        parseDateTime(json.getString(Constants.CREATED_AT)),
        parseDateTime(json.getString(Constants.UPDATED_AT))
      );
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (id != null) json.put(Constants.COMPUTE_ROLE_ID, id.toString());
    json.put(Constants.USER_ID, userId.toString());
    if (userName != null) json.put(Constants.USER_NAME, userName);
    if (status != null && !status.isEmpty()) json.put(Constants.STATUS, status);
    if (approvedBy != null) json.put(Constants.APPROVED_BY, approvedBy.toString());
    if (additionalInfo != null) json.put(Constants.ADDITONAL_INFO, additionalInfo); // <-- new field
    if (createdAt != null) json.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) json.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (id != null) map.put(Constants.COMPUTE_ROLE_ID, id.toString());
    if (userId != null) map.put(Constants.USER_ID, userId.toString());
    if  (userName!=null) map.put(Constants.USER_NAME, userName.toString());
    if (status != null && !status.isEmpty()) map.put(Constants.STATUS, status);
    if (approvedBy != null) map.put(Constants.APPROVED_BY, approvedBy.toString());
    if (additionalInfo != null) map.put(Constants.ADDITONAL_INFO, additionalInfo);
    if (createdAt != null) map.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));
    return map;
  }

  @Override
  public String getTableName() {
    return Constants.COMPUTE_ROLE_TABLE;
  }
}
