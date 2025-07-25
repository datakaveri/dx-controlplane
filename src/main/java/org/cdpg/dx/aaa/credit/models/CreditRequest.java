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

public record CreditRequest(
  UUID id,
  UUID userId,
  String userName,
  JsonObject additionalInfo,
  String status,
  LocalDateTime requestedAt,
  LocalDateTime processedAt
) implements BaseEntity<CreditRequest> {

  public static CreditRequest fromJson(JsonObject json) {
    try {
      return new CreditRequest(
        json.getString(Constants.CREDIT_REQUEST_ID) != null
          ? UUID.fromString(json.getString(Constants.CREDIT_REQUEST_ID))
          : null,
        UUID.fromString(requireNonNull(json.getString(Constants.USER_ID), Constants.USER_ID)),
        requireNonNull(json.getString(Constants.USER_NAME),Constants.USER_NAME),
        json.getJsonObject(Constants.ADDITONAL_INFO),
        json.getString(Constants.STATUS) != null
          ? json.getString(Constants.STATUS)
          : Status.PENDING.getStatus(),
        parseDateTime(json.getString(Constants.REQUESTED_AT)),
        parseDateTime(json.getString(Constants.PROCESSED_AT))
      );
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put(Constants.CREDIT_REQUEST_ID, id.toString());
    json.put(Constants.USER_ID, userId.toString());
    json.put(Constants.USER_NAME, userName.toString());
    if (additionalInfo != null) json.put(Constants.ADDITONAL_INFO, additionalInfo); // <-- new field
    if (status != null && !status.isEmpty()) json.put(Constants.STATUS, status);
    if (requestedAt != null) json.put(Constants.REQUESTED_AT, requestedAt.format(FORMATTER));
    if (processedAt != null) json.put(Constants.PROCESSED_AT, processedAt.format(FORMATTER));

    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put(Constants.CREDIT_REQUEST_ID, id.toString());
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.USER_NAME, userName.toString());
    if (additionalInfo != null) map.put(Constants.ADDITONAL_INFO, additionalInfo);
    if (status != null && !status.isEmpty()) map.put(Constants.STATUS, status);
    if (requestedAt != null) map.put(Constants.REQUESTED_AT, requestedAt.format(FORMATTER));
    if (processedAt != null) map.put(Constants.PROCESSED_AT, processedAt.format(FORMATTER));

    return map;
  }

  @Override
  public String getTableName() {
    return Constants.CREDIT_REQUEST_TABLE;
  }
}
