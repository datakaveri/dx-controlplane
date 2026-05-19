package org.cdpg.dx.auth.authentication.util;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.common.exception.DxForbiddenException;

import java.util.List;

public class AccessValidator {

  private static final Logger LOGGER = LogManager.getLogger(AccessValidator.class);

  /**
   * Rules:
   * 1. Primary roles → always allowed
   * 2. Delegate:
   *    - delegation_scope contains "*" → allowed
   *    - OR contains required scope → allowed
   * 3. Otherwise → forbidden
   */
  public static void validate(
    JsonObject userJson,
    List<String> primaryRoles,
    List<String> requiredScopes
  ) {

    JsonArray userRoles =
      userJson
        .getJsonObject("realm_access", new JsonObject())
        .getJsonArray("roles", new JsonArray());

    // ---------------- PRIMARY USER ----------------
    boolean isPrimaryUser =
      primaryRoles.stream().anyMatch(userRoles::contains);

    if (isPrimaryUser) {
      LOGGER.debug("Primary role access granted");
      return;
    }

    // ---------------- DELEGATE USER ----------------
    boolean isDelegate = userRoles.contains("delegate");

    if (isDelegate) {
      JsonArray delegationScopes =
        userJson.getJsonArray("delegation_scope", new JsonArray());

      // Wildcard delegation
      if (delegationScopes.contains("*")) {
        LOGGER.debug("Wildcard delegation access granted");
        return;
      }

      // Scope-based delegation
      boolean hasRequiredScope =
        requiredScopes.stream().anyMatch(delegationScopes::contains);

      if (!hasRequiredScope) {
        throw new DxForbiddenException(
          "Missing required delegation scope(s): " + requiredScopes
        );
      }
      LOGGER.debug("Scoped delegation access granted");
      return;
    }

    // ---------------- FORBIDDEN ----------------
    throw new DxForbiddenException("User is not allowed to access this API.");
  }
}
