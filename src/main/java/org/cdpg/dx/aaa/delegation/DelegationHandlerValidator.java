package org.cdpg.dx.aaa.delegation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.model.DxUser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DelegationHandlerValidator {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  public void validateCreateDelegationGrantBody(UUID userId, JsonObject body) {
    body.put("delegator_id", userId);

    String expirationDate = body.getString("expiry_at");
    if (expirationDate == null || expirationDate.isBlank()) {
      throw new DxBadRequestException("Expiration date is required");
    }

    LocalDateTime expiry;
    try {
      expiry = LocalDateTime.parse(expirationDate, FORMATTER);
    } catch (DateTimeParseException e) {
      throw new DxBadRequestException("Invalid expiration date format. Expected format: " + FORMATTER);
    }

    if (expiry.isBefore(LocalDateTime.now())) {
      throw new DxBadRequestException("Expiration date must be in the future");
    }

    JsonArray constraintsArray = body.getJsonArray("constraints", new JsonArray());
    for (int i = 0; i < constraintsArray.size(); i++) {
      JsonObject constraint = constraintsArray.getJsonObject(i);
      String scope = constraint.getString("scope");
      if (scope == null || scope.isBlank()) {
        throw new DxBadRequestException("Scope is required in constraint at index " + i);
      }
    }
  }

  public void validateCreateUpdateDelegationRequestBody(JsonObject body) {
    String delegationIdStr = body.getString("delegation_id");
    if (delegationIdStr == null || delegationIdStr.isBlank()) {
      throw new DxBadRequestException("delegation_id is required");
    }

    String justification = body.getString("justification");
    if (justification == null || justification.isBlank()) {
      throw new DxBadRequestException("Justification is required");
    }

    String requestedExpiryStr = body.getString("requested_expiry");
    if (requestedExpiryStr == null || requestedExpiryStr.isBlank()) {
      throw new DxBadRequestException("requested_expiry is required");
    }

    LocalDateTime requestedExpiry = parseDateTime(requestedExpiryStr);
    if (requestedExpiry.isBefore(LocalDateTime.now())) {
      throw new DxBadRequestException("requested_expiry must be in the future");
    }

    JsonArray requestedScopes = body.getJsonArray("requested_scopes");
    if (requestedScopes == null) {
      Object scopesObj = body.getValue("requested_scopes");
      if (scopesObj instanceof String str) {
        requestedScopes = new JsonArray(str);
      } else {
        throw new DxBadRequestException("requested_scopes must be a JSON array");
      }
    }
    requestedScopes.forEach(o -> {
      if (!(o instanceof JsonObject)) {
        throw new DxBadRequestException("requested_scopes must be JSON objects");
      }
    });
  }

  public Set<String> extractRoles(User user) {
    Set<String> roles = new HashSet<>();
    JsonObject principal = user.principal();
    if (principal.containsKey("realm_access")) {
      JsonObject realmAccess = principal.getJsonObject("realm_access");
      if (realmAccess.containsKey("roles")) {
        roles.addAll(realmAccess.getJsonArray("roles").getList());
      }
    }
    return roles;
  }

  public List<JsonObject> extractConstraintsforDelegationGrants(JsonObject body) {
    return body.getJsonArray("constraints", new JsonArray())
      .stream()
      .map(o -> (JsonObject) o)
      .toList();

  }

  public List<JsonObject> extractConstraintsforDelegationRequests(JsonObject body) {
    return body.getJsonArray("requested_scopes", new JsonArray())
      .stream()
      .map(o -> (JsonObject) o)
      .toList();

  }

  private LocalDateTime parseDateTime(String str) {
    try {
      return LocalDateTime.parse(str, FORMATTER);
    } catch (DateTimeParseException e) {
      throw new DxBadRequestException("Invalid date format. Expected: " + FORMATTER);
    }
  }
}
