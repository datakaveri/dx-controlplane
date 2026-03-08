package org.cdpg.dx.auth.authorization.handler;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthorizationHandler {
  private static final Logger LOGGER = LogManager.getLogger(AuthorizationHandler.class);

  public static Handler<RoutingContext> forRoles(DxRole... roles) {
    Set<String> allowed =
        Arrays.stream(roles)
            .map(DxRole::getRole)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

    boolean isOnlyCompute = roles.length == 1 && roles[0] == DxRole.COMPUTE;

    return ctx -> {
      User user = ctx.user();
      if (user == null) {
        ctx.fail(new DxUnauthorizedException("User not authenticated.")); // HTTP 401
        return;
      }

      JsonObject principal = user.principal();
      JsonObject realmAccess = principal.getJsonObject("realm_access");

      if (realmAccess == null || !realmAccess.containsKey("roles")) {
        ctx.fail(new DxForbiddenException("No roles assigned to the user.")); // HTTP 403
        return;
      }

      JsonArray userRoles = realmAccess.getJsonArray("roles");

      boolean allowedRole =
          userRoles.stream()
              .map(Object::toString)
              .map(String::toLowerCase)
              .anyMatch(allowed::contains);

      List<String> matchedRoles =
          userRoles.stream()
              .map(Object::toString)
              .map(String::toLowerCase)
              .filter(allowed::contains)
              .collect(Collectors.toList());

      if (!matchedRoles.isEmpty()) {
        ctx.put("allowedRoles", matchedRoles);
      }

      if (allowedRole) {
        ctx.next();
      } else {
        if (isOnlyCompute) {
          ctx.fail(
              new DxForbiddenException(
                  "Please upgrade your role to access GPU-based compute.")); // HTTP 403
        } else {
          ctx.fail(new DxForbiddenException("User does not have the required role.")); // HTTP 403
        }
      }
    };
  }

  public static Handler<RoutingContext> forDelegationScopes(DxScope... scopes) {

    Set<String> allowed =
      Arrays.stream(scopes)
        .map(DxScope::getScope)
        .map(String::toLowerCase)
        .collect(Collectors.toSet());

    return ctx -> {
      User user = ctx.user();
      if (user == null) {
        ctx.fail(new DxUnauthorizedException("User not authenticated."));
        return;
      }

      JsonObject principal = user.principal();

      // If primary user (has realm_access.roles), skip delegation scope check
      JsonArray realmRoles = principal
        .getJsonObject("realm_access", new JsonObject())
        .getJsonArray("roles", new JsonArray());

      boolean isPrimaryUser = realmRoles.stream()
        .map(Object::toString)
        .anyMatch(role -> !role.equalsIgnoreCase("delegate"));

      if (isPrimaryUser) {
        LOGGER.debug("Skipping delegation scope check for primary user");
        ctx.next();
        return;
      }

      // Delegate user — check delegation_scope
      JsonArray delegationScopes = principal.getJsonArray("delegation_scope");

      if (delegationScopes == null) {
        ctx.fail(new DxForbiddenException("No delegation scope assigned to the user."));
        return;
      }

      boolean delegationPresent =
        delegationScopes.stream()
          .map(Object::toString)
          .map(String::toLowerCase)
          .anyMatch(allowed::contains);

      List<String> matchedScopes =
        delegationScopes.stream()
          .map(Object::toString)
          .map(String::toLowerCase)
          .filter(allowed::contains)
          .collect(Collectors.toList());

      if (!matchedScopes.isEmpty()) {
        ctx.put("allowedScopes", matchedScopes);
      }

      if (delegationPresent) {
        ctx.next();
      } else {
        ctx.fail(new DxForbiddenException("User does not have the required scope."));
      }
    };
  }

  public static Handler<RoutingContext> KycVerification(Boolean isKycRequired) {
    if (!isKycRequired) {
      return RoutingContext::next;
    }

    return ctx -> {
      User user = ctx.user();
      if (user == null) {
        ctx.fail(new DxUnauthorizedException("User not authenticated.")); // HTTP 401
        return;
      }

      JsonObject principal = user.principal();

      if (principal == null || !principal.containsKey("kyc_verified")) {
        ctx.fail(new DxForbiddenException("Missing KYC verification status.")); // HTTP 403
        return;
      }

      boolean isKycVerified = principal.getBoolean("kyc_verified", false);
      if (!isKycVerified) {
        ctx.fail(new DxForbiddenException("User's KYC is not verified.")); // HTTP 403
        return;
      }

      ctx.next();
    };
  }
}
