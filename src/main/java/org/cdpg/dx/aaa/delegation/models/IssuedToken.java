package org.cdpg.dx.aaa.delegation.models;


import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record IssuedToken(
  UUID jti,
  UUID delegationId,
  UUID delegateId,
  JsonObject scopes,
  LocalDateTime expiresAt,
  Boolean revoked,
  LocalDateTime revokedAt,
  LocalDateTime createdAt
) implements BaseEntity<IssuedToken> {

  public static IssuedToken fromJson(JsonObject json) {
    try {
      return new IssuedToken(
        json.getString("jti") != null ? UUID.fromString(json.getString("jti")) : null,
        json.getString("delegation_id") != null ? UUID.fromString(json.getString("delegation_id")) : null,
        requireNonNull(
          json.getString("delegate_id") != null ? UUID.fromString(json.getString("delegate_id")) : null,
          "delegate_id"
        ),
        requireNonNull(json.getJsonObject("scopes"), "scopes"),
        requireNonNull(parseDateTime(json.getString("expires_at")), "expires_at"),
        json.getBoolean("revoked") != null ? json.getBoolean("revoked") : Boolean.FALSE,
        json.getString("revoked_at") != null ? parseDateTime(json.getString("revoked_at")) : null,
        json.getString("created_at") != null ? parseDateTime(json.getString("created_at")) : null
      );
    } catch (Exception e) {
      throw new DxValidationException("Invalid or missing field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (jti != null) json.put("jti", jti.toString());
    if (delegationId != null) json.put("delegation_id", delegationId.toString());
    if (delegateId != null) json.put("delegate_id", delegateId.toString());
    if (scopes != null) json.put("scopes", scopes);
    if (expiresAt != null) json.put("expires_at", expiresAt.format(FORMATTER));
    if (revoked != null) json.put("revoked", revoked);
    if (revokedAt != null) json.put("revoked_at", revokedAt.format(FORMATTER));
    if (createdAt != null) json.put("created_at", createdAt.format(FORMATTER));
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (jti != null) map.put("jti", jti.toString());
    if (delegationId != null) map.put("delegation_id", delegationId.toString());
    if (delegateId != null) map.put("delegate_id", delegateId.toString());
    if (scopes != null) map.put("scopes", scopes);
    if (expiresAt != null) map.put("expires_at", expiresAt.format(FORMATTER));
    if (revoked != null) map.put("revoked", revoked);
    if (revokedAt != null) map.put("revoked_at", revokedAt.format(FORMATTER));
    if (createdAt != null) map.put("created_at", createdAt.format(FORMATTER));
    return map;
  }

  @Override
  public String getTableName() {
    return "issued_tokens";
  }
}
