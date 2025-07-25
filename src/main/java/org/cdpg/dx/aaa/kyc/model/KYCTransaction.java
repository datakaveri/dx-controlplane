package org.cdpg.dx.aaa.kyc.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.kyc.util.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record KYCTransaction(
    UUID userId,
    String transactionId,
    String codeVerifier,
    Boolean isConfirmed,
    LocalDateTime updatedAt
  ) implements BaseEntity<KYCTransaction>  {
    public static KYCTransaction fromJson(JsonObject json) {
      try {
        return new KYCTransaction(
          UUID.fromString(json.getString(Constants.USER_ID)),
          json.getString(Constants.TRANSACTION_ID),
          json.getString(Constants.CODE_VERIFIER),
          json.getBoolean(Constants.CONFIRMED_FLAG),
          LocalDateTime.parse(json.getString(Constants.UPDATED_AT))
        );
      } catch (Exception e) {
        throw new DxValidationException("Missing or invalid required field: " + e.getMessage());

      }

    }

    @Override
    public JsonObject toJson() {
        return new JsonObject()
            .put(Constants.USER_ID, userId.toString())
            .put(Constants.TRANSACTION_ID, transactionId.toString())
            .put(Constants.CODE_VERIFIER, codeVerifier)
            .put(Constants.CONFIRMED_FLAG, isConfirmed)
            .put(Constants.UPDATED_AT, updatedAt.toString());
    }

    @Override
    public Map<String,Object> toNonEmptyFieldsMap( ) {
        Map<String, Object> map = new HashMap<>();
        if (userId != null) map.put(Constants.USER_ID, userId.toString());
        if (transactionId != null) map.put(Constants.TRANSACTION_ID, transactionId.toString());
        if (codeVerifier != null) map.put(Constants.CODE_VERIFIER, codeVerifier);
        if (isConfirmed != null) map.put(Constants.CONFIRMED_FLAG, isConfirmed);
        if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.toString());
        return map;
    }

  @Override
  public String getTableName() {
    return Constants.KYC_TRANSACTION_TABLE;
  }


}

