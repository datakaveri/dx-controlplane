package org.cdpg.dx.aaa.delegation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authorization.registry.SystemRoleScopeMap;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.model.DxUser;

public class DelegationHandlerValidator {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
  private static final Logger LOGGER = LogManager.getLogger(DelegationHandlerValidator.class);

  public void validateCreateDelegationGrantBody(
      UUID userId, List<String> delegatorRoles, JsonObject body) {

    // *****************************************************************************************************************

    String expirationDate = body.getString("expiryAt");
    if (expirationDate == null || expirationDate.isBlank()) {
      throw new DxBadRequestException("expiryAt");
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

    //*****************************************************************************************************************

    JsonArray rolesArray = body.getJsonArray("roles");

    // Full delegation (wildcard for all roles & scopes)
    if (rolesArray == null || rolesArray.isEmpty()) {
      LOGGER.info("Delegation/AppKey creation gives full access to all roles and scopes of delegator");
      return;
    }

    for (int i = 0; i < rolesArray.size(); i++) {
      JsonObject roleObj = rolesArray.getJsonObject(i);
      String role = roleObj.getString("role");
      if (role == null || role.isBlank()) {
        throw new DxBadRequestException("Role is required in roles array");
      }

      //      if(delegatorRoles.contains("cos_admin"))
      //      {
      //        LOGGER.info("This user can have roles provider, org_admin and compute - no need to
      // check the role provided in roles constraint ");
      // return;
      //      }
      //      else
      if (!delegatorRoles.contains(role)) {
        throw new DxBadRequestException("The delegator/user doesnot have the role "+ role);
      }

      JsonArray constraints = roleObj.getJsonArray("constraints");
      if (constraints == null || constraints.isEmpty()) {
        LOGGER.info("Delegate/User has full access to the given role");
        return;
      }

      for (int j = 0; j < constraints.size(); j++) {
        JsonObject constraint = constraints.getJsonObject(j);
        String scope = constraint.getString("scope");
        String entityId = constraint.getString("entityId");
        String entityType = constraint.getString("entityType");

        if (scope == null || scope.isBlank()) {
          throw new DxBadRequestException("Scope is required in constraints");
        }

        Set<String> allowedScopes = DxRole.fromString(role)
            .map(SystemRoleScopeMap::getScopes)
            .orElse(Set.of());
        if (!allowedScopes.contains(scope)) {
          throw new DxBadRequestException("The role that the user is giving doesnt allow the scope " + scope);
        }

        // ---------- Entity pairing validation ----------
        boolean entityIdPresent = entityId != null;
        boolean entityTypePresent = entityType != null;

        if (entityIdPresent != entityTypePresent) {
          throw new DxBadRequestException(
              "Both entityId and entityType must be provided together for scope: " + scope);
        }

        // ---------- Constraint expiry validation ----------
        // Optional: a constraint with no expiryAt inherits the grant's own expiryAt
        // (see DelegationServiceImpl#createScopeConstraint), so only validate when present.
        String constraintExpiryStr = constraint.getString("expiryAt");
        if (constraintExpiryStr != null && !constraintExpiryStr.isBlank()) {
          LocalDateTime constraintExpiry;
          try {
            constraintExpiry = LocalDateTime.parse(constraintExpiryStr, FORMATTER);
          } catch (DateTimeParseException e) {
            throw new DxBadRequestException(
                "Invalid expiryAt format for constraint scope " + scope + ". Expected format: "
                    + FORMATTER);
          }

          if (constraintExpiry.isBefore(LocalDateTime.now())) {
            throw new DxBadRequestException(
                "Constraint expiryAt for scope " + scope + " must be in the future");
          }

          if (constraintExpiry.isAfter(expiry)) {
            throw new DxBadRequestException(
                "Constraint expiryAt for scope " + scope
                    + " must not be after the delegation's expiryAt");
          }
        }

        // entity_id & entity_type both absent → implicit wildcard
        if (!entityIdPresent) {
          LOGGER.info(
              "Scope {} granted for role {} on all entities (implicit wildcard)", scope, role);
        } else {
          LOGGER.info(
              "Scope {} granted for role {} on entity {} ({})", scope, role, entityId, entityType);
        }
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

  public List<String> extractRoles(DxUser user) {

    return user.roles();
  }


  private LocalDateTime parseDateTime(String str) {
    try {
      return LocalDateTime.parse(str, FORMATTER);
    } catch (DateTimeParseException e) {
      throw new DxBadRequestException("Invalid date format. Expected: " + FORMATTER);
    }
  }
}
