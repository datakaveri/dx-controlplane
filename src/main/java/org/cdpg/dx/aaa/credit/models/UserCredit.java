package org.cdpg.dx.aaa.credit.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.util.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record UserCredit(
  UUID id,
  UUID userId,
  double balance,
  LocalDateTime updatedAt
) implements BaseEntity<UserCredit> {

  public static UserCredit fromJson(JsonObject json) {
    try {
      return new UserCredit(
        json.containsKey(Constants.USER_CREDIT_ID)
          ? UUID.fromString(json.getString(Constants.USER_CREDIT_ID))
          : null,
        UUID.fromString(requireNonNull(json.getString(Constants.USER_ID), Constants.USER_ID)),
        json.getDouble(Constants.BALANCE),
        parseDateTime(json.getString(Constants.UPDATED_AT))
      );
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (id != null) json.put(Constants.USER_CREDIT_ID, id.toString());
    json.put(Constants.USER_ID, userId.toString());
    json.put(Constants.BALANCE, balance);
    if (updatedAt != null) json.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (id != null) map.put(Constants.USER_CREDIT_ID, id);
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.BALANCE, balance);
    if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));
    return map;
  }

  @Override
  public String getTableName() {
    return Constants.USER_CREDIT_TABLE;
  }
}
