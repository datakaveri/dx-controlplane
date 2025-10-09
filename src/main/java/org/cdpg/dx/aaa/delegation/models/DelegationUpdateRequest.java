package org.cdpg.dx.aaa.delegation.models;

import io.vertx.core.json.JsonArray;
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

public record DelegationUpdateRequest(
  UUID requestId,
  UUID delegationId,
  UUID requesterId,
  JsonArray requestedScopes,
  LocalDateTime requestedExpiry,
//  LocalDateTime localExpiry,
  String justification,
  String status,
  LocalDateTime createdAt,
  LocalDateTime reviewedAt,
  UUID reviewerId
) implements BaseEntity<DelegationUpdateRequest> {

  public static DelegationUpdateRequest fromJson(JsonObject json) {
    try {
      return new DelegationUpdateRequest(
        json.getString("request_id") != null ? UUID.fromString(json.getString("request_id")) : null,
        requireNonNull(
          json.getString("delegation_id") != null ? UUID.fromString(json.getString("delegation_id")) : null,
          "delegation_id"
        ),
        requireNonNull(
          json.getString("requester_id") != null ? UUID.fromString(json.getString("requester_id")) : null,
          "requester_id"
        ),
        requireNonNull(parseRequestedScopes(json.getValue("requested_scopes")), "requested_scopes"),
        json.getString("requested_expiry") != null ? parseDateTime(json.getString("requested_expiry")) : null,
//        json.getString("expiry_at") != null ? parseDateTime(json.getString("expiry_at")) : null,
        requireNonNull(json.getString("justification"), "justification"),
        json.getString("status") != null ? json.getString("status") : "pending",
        json.getString("created_at") != null ? parseDateTime(json.getString("created_at")) : null,
        json.getString("reviewed_at") != null ? parseDateTime(json.getString("reviewed_at")) : null,
        json.getString("reviewer_id") != null ? UUID.fromString(json.getString("reviewer_id")) : null
      );
    } catch (Exception e) {
      throw new DxValidationException("Invalid or missing field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (requestId != null) json.put("request_id", requestId.toString());
    if (delegationId != null) json.put("delegation_id", delegationId.toString());
    if (requesterId != null) json.put("requester_id", requesterId.toString());
    if (requestedScopes != null) json.put("requested_scopes", requestedScopes);
    if (requestedExpiry != null) json.put("requested_expiry", requestedExpiry.format(FORMATTER));
//    if (localExpiry != null) json.put("expiry_at", localExpiry.format(FORMATTER));
    if (justification != null) json.put("justification", justification);
    if (status != null) json.put("status", status);
    if (createdAt != null) json.put("created_at", createdAt.format(FORMATTER));
    if (reviewedAt != null) json.put("reviewed_at", reviewedAt.format(FORMATTER));
    if (reviewerId != null) json.put("reviewer_id", reviewerId.toString());
    return json;
  }

  private static JsonArray parseRequestedScopes(Object value) {
    if (value == null) return null;

    if (value instanceof JsonArray jsonArray) {
      return jsonArray;
    } else if (value instanceof String str) {
      try {
        return new JsonArray(str); // convert string to JsonArray
      } catch (Exception e) {
        throw new DxValidationException("Invalid requested_scopes JSON format");
      }
    } else {
      throw new DxValidationException("Invalid type for requested_scopes: " + value.getClass());
    }
  }


  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (requestId != null) map.put("request_id", requestId.toString());
    if (delegationId != null) map.put("delegation_id", delegationId.toString());
    if (requesterId != null) map.put("requester_id", requesterId.toString());
    if (requestedScopes != null) map.put("requested_scopes", requestedScopes.encode()); // full objects
    if (requestedExpiry != null) map.put("requested_expiry", requestedExpiry.format(FORMATTER));
//    if (localExpiry != null) map.put("expiry_at", localExpiry.format(FORMATTER));

    if (justification != null) map.put("justification", justification);
    if (status != null) map.put("status", status);
    if (createdAt != null) map.put("created_at", createdAt.format(FORMATTER));
    if (reviewedAt != null) map.put("reviewed_at", reviewedAt.format(FORMATTER));
    if (reviewerId != null) map.put("reviewer_id", reviewerId.toString());
    return map;
  }

  @Override
  public String getTableName() {
    return "delegation_update_requests";
  }
}
