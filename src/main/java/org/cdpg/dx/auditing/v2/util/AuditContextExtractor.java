package org.cdpg.dx.auditing.v2.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.RoutingContextHelper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class AuditContextExtractor {

  private AuditContextExtractor() {}

  /* -------------------------------------------------
   * Core helpers
   * ------------------------------------------------- */

  private static JsonObject principal(User user) {
    return user == null ? null : user.principal();
  }

  /* -------------------------------------------------
   * User context
   * ------------------------------------------------- */

  public static UUID getUserId(RoutingContext ctx) {
    return ctx == null ? null : getUserId(ctx.user());
  }

  public static UUID getUserId(User user) {
    JsonObject p = principal(user);
    if (p == null) return null;

    String sub = p.getString("sub");
    try {
      return sub == null ? null : UUID.fromString(sub);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static String getUserName(User user) {
    JsonObject p = principal(user);
    return p == null ? null : p.getString("name");
  }

  public static String getPreferredUsername(User user) {
    JsonObject p = principal(user);
    return p == null ? null : p.getString("preferred_username");
  }

  public static String getEmail(User user) {
    JsonObject p = principal(user);
    return p == null ? null : p.getString("email");
  }

  /* -------------------------------------------------
   * Organisation context
   * ------------------------------------------------- */

  public static UUID getOrgId(User user) {
    JsonObject p = principal(user);
    if (p == null) return null;

    String orgId = p.getString("organisation_id");
    try {
      return orgId == null ? null : UUID.fromString(orgId);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static String getOrgName(User user) {
    JsonObject p = principal(user);
    return p == null ? null : p.getString("organisation_name");
  }

  /**
   * IMPORTANT: org_type is NOT present in JWT. Must be resolved from DB / org service (outside this
   * class).
   */
  public static String getOrgType() {
    return null;
  }

  /* -------------------------------------------------
   * Role & delegation
   * ------------------------------------------------- */

  public static List<String> getRealmRoles(User user) {
    JsonObject p = principal(user);
    if (p == null) return List.of();

    JsonObject realmAccess = p.getJsonObject("realm_access");
    if (realmAccess == null) return List.of();

    JsonArray roles = realmAccess.getJsonArray("roles");
    return roles == null ? List.of() : roles.getList();
  }

  /** Returns highest-priority role based on platform rules. */
  public static String getEffectiveRole(User user) {
    List<String> roles = getRealmRoles(user);

    if (roles.contains("cos_admin")) return "cos_admin";
    if (roles.contains("org_admin")) return "org_admin";
    if (roles.contains("provider")) return "provider";
    if (roles.contains("consumer")) return "consumer";

    return null;
  }

  /**
   * When the request went through header-based delegation (the {@code did} header),
   * {@code ctx.user()}'s sub is swapped to the delegator (primary user) and the original
   * caller's id is stamped on the resolved {@link DxUser} as {@code delegateeId}. Returns null
   * for non-delegated requests.
   */
  public static UUID getDelegateeId(RoutingContext ctx) {
    if (ctx == null) return null;

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    if (dxUser == null || dxUser.delegateeId() == null) return null;

    try {
      return UUID.fromString(dxUser.delegateeId());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /* -------------------------------------------------
   * Token metadata
   * ------------------------------------------------- */

  public static String getIssuer(User user) {
    JsonObject p = principal(user);
    return p == null ? null : p.getString("iss");
  }

  public static String getAudience(User user) {
    JsonObject p = principal(user);
    return p == null ? null : p.getString("aud");
  }

  /* -------------------------------------------------
   * Request metadata
   * ------------------------------------------------- */

  public static String getClientIp(RoutingContext ctx) {
    if (ctx == null || ctx.request() == null) return null;
    return ctx.request().remoteAddress().host();
  }

  public static String getUserAgent(RoutingContext ctx) {
    if (ctx == null || ctx.request() == null) return null;
    return ctx.request().getHeader("User-Agent");
  }

  /**
   * Returns API TEMPLATE, not resolved path. Example:
   * /iudx/v2/auth/organisations/{id}/join_requests
   */
  public static String getApiTemplate(RoutingContext ctx) {
    if (ctx == null) return null;

    String routePath = ctx.currentRoute() != null ? ctx.currentRoute().getPath() : null;

    String mountPoint = ctx.mountPoint();

    if (routePath == null) {
      return ctx.normalizedPath();
    }

    String fullPath = mountPoint == null ? routePath : mountPoint + routePath;

    // Convert :id → {id}
    return fullPath.replaceAll(":([^/]+)", "\\{$1\\}");
  }

  /** Returns actual resolved path (with UUIDs). */
  public static String getApiPath(RoutingContext ctx) {
    return ctx == null ? null : ctx.normalizedPath();
  }

  public static String getHttpMethod(RoutingContext ctx) {
    return ctx == null || ctx.request() == null ? null : ctx.request().method().name();
  }

  /* -------------------------------------------------
   * Time helpers
   * ------------------------------------------------- */

  public static LocalDateTime now() {
    return LocalDateTime.now();
  }
}
