package org.cdpg.dx.aaa.credit.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.util.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.*;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record CreditTransaction(
        UUID id,
        UUID userId,
        Double amount,
        UUID transactedBy,
        String transactionStatus,
        String transactionType,
        LocalDateTime createdAt,
        LocalDateTime requestedAt,
        Double updatedBalance  // ✅ New field
) implements BaseEntity<CreditTransaction> {

  public static CreditTransaction fromJson(JsonObject json) {
    try {
      return new CreditTransaction(
              json.containsKey(Constants.CREDIT_TRANSACTION_ID)
                      ? UUID.fromString(json.getString(Constants.CREDIT_TRANSACTION_ID))
                      : null,
              UUID.fromString(requireNonNull(json.getString(Constants.USER_ID), Constants.USER_ID)),
              json.getDouble(Constants.AMOUNT),
              json.containsKey(Constants.TRANSACTED_BY)
                      ? UUID.fromString(json.getString(Constants.TRANSACTED_BY))
                      : null,
              json.getString(Constants.TRANSACTION_STATUS),
              json.getString(Constants.TRANSACTION_TYPE),
              parseDateTime(json.getString(Constants.CREATED_AT)),
              parseRequiredDateTime(json.getString(Constants.REQUESTED_AT), Constants.REQUESTED_AT),
              json.getDouble(Constants.UPDATED_BALANCE) // ✅ From JSON
      );
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (id != null) json.put(Constants.CREDIT_TRANSACTION_ID, id.toString());
    if (userId != null) json.put(Constants.USER_ID, userId.toString());
    if (amount != null) json.put(Constants.AMOUNT, amount);
    if (transactedBy != null) json.put(Constants.TRANSACTED_BY, transactedBy.toString());
    if (transactionStatus != null && !transactionStatus.isEmpty())
      json.put(Constants.TRANSACTION_STATUS, transactionStatus);
    if (transactionType != null && !transactionType.isEmpty())
      json.put(Constants.TRANSACTION_TYPE, transactionType);
    if (createdAt != null)
      json.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (requestedAt != null)
      json.put(Constants.REQUESTED_AT, requestedAt.format(FORMATTER));
    if (updatedBalance != null)
      json.put(Constants.UPDATED_BALANCE, updatedBalance); // ✅ To JSON
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (id != null) map.put(Constants.CREDIT_TRANSACTION_ID, id.toString());
    if (userId != null) map.put(Constants.USER_ID, userId.toString());
    if (amount != null) map.put(Constants.AMOUNT, amount);
    if (transactedBy != null) map.put(Constants.TRANSACTED_BY, transactedBy.toString());
    if (transactionStatus != null && !transactionStatus.isEmpty())
      map.put(Constants.TRANSACTION_STATUS, transactionStatus);
    if (transactionType != null && !transactionType.isEmpty())
      map.put(Constants.TRANSACTION_TYPE, transactionType);
    if (createdAt != null)
      map.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (requestedAt != null)
      map.put(Constants.REQUESTED_AT, requestedAt.format(FORMATTER));
    if (updatedBalance != null)
      map.put(Constants.UPDATED_BALANCE, updatedBalance); // ✅ Non-empty map
    return map;
  }

  @Override
  public String getTableName() {
    return Constants.CREDIT_TRANSACTION_TABLE;
  }
}
