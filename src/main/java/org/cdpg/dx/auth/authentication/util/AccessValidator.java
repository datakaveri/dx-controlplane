package org.cdpg.dx.auth.authentication.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.exception.DxForbiddenException;
import java.util.List;

public class AccessValidator {
  private static final Logger LOGGER = LogManager.getLogger(AccessValidator.class);

  /**
   * Access Rule: ✔ Primary roles → allowed directly ✔ Delegate → must match required scope(s) ✔
   * Others → forbidden
   */
  public static void validate(
    JsonObject userJson, List<String> primaryRoles, List<String> requiredScopes) {

    LOGGER.error("userJosn {} ", userJson);

    JsonArray userRoles =
      userJson
        .getJsonObject("realm_access", new JsonObject())
        .getJsonArray("roles", new JsonArray());

    // ---- Primary users → Always allowed ----

    boolean isPrimaryUser = primaryRoles.stream().anyMatch(userRoles::contains);
    LOGGER.debug("isPrimary : {} ", isPrimaryUser);
    if (isPrimaryUser) {
      return;
    }

    // ---- Delegate user → Must have required scopes ----
    boolean isDelegate = userRoles.contains(DxRole.DELEGATE.getRole());
    LOGGER.debug("isDelegte : {}", isDelegate);

    if (isDelegate) {
      JsonArray scopes = userJson.getJsonArray("delegation_scope", new JsonArray());

      boolean hasRequiredScope = requiredScopes.stream().anyMatch(scopes::contains);
      LOGGER.debug("hasRequiredScope : {} ", hasRequiredScope);

      if (!hasRequiredScope) {
        throw new DxForbiddenException("Missing required scope(s): " + requiredScopes);
      }
      return;
    }

    // ---- Neither primary nor delegate → Forbidden ----
    throw new DxForbiddenException("User is not allowed to access this API.");
  }
}
