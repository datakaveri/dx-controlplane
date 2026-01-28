package org.cdpg.dx.auditing.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.auth.authorization.model.DxRole;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public final class AuditContextExtractor {

  private AuditContextExtractor() {}

  public static UUID getUserId(RoutingContext ctx) {
    User user = ctx.user();
    if (user == null) return null;

    String subject = user.subject();
    if (subject == null || subject.isBlank()) return null;

    try {
      return UUID.fromString(subject);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static JsonArray getRealmRoles(User user) {
    if (user == null || user.principal() == null) {
      return new JsonArray();
    }
    JsonObject principal = user.principal();
    return principal
        .getJsonObject("realm_access", new JsonObject())
        .getJsonArray("roles", new JsonArray());
  }

  public static DxRole getUserRole(User user) {
    JsonArray roles = getRealmRoles(user);

    if (roles.contains("delegate")) return DxRole.DELEGATE;
    if (roles.contains("cos_admin")) return DxRole.COS_ADMIN;
    if (roles.contains("org_admin")) return DxRole.ORG_ADMIN;
    if (roles.contains("provider")) return DxRole.PROVIDER;
    if (roles.contains("consumer")) return DxRole.CONSUMER;
    if (roles.contains("compute")) return DxRole.COMPUTE;

    return DxRole.CONSUMER;
  }

  public static boolean isDelegate(User user) {
    JsonArray roles = getRealmRoles(user);
    return roles.contains("delegate");
  }

  public static UUID getDelegatorId(User user) {
    if (!isDelegate(user)) return null;

    String id = user.principal().getString("delegator_id");
    if (id == null || id.isBlank()) return null;

    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  public static DxRole getDelegatorRole(User user) {
    if (!isDelegate(user)) return null;

    String role = user.principal().getString("delegator_role");
    if (role == null || role.isBlank()) {
      return null;
    }

    return DxRole.fromString(role).orElse(null);
  }

  public static String getIssuer(User user) {
    if (user == null) return null;

    JsonObject json = user.principal();
    return json.getString("iss");
  }

  public static String getClientIp(RoutingContext ctx) {
    String forwarded = ctx.request().getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }

    if (ctx.request().remoteAddress() != null) {
      return ctx.request().remoteAddress().host();
    }

    return "unknown";
  }

  public static String getUserAgent(RoutingContext ctx) {
    String ua = ctx.request().getHeader("User-Agent");
    if (ua == null || ua.isBlank()) {
      return "unknown";
    }
    return ua;
  }

  public static Long getRequestSize(RoutingContext ctx) {
    String len = ctx.request().getHeader("Content-Length");
    if (len == null || len.isBlank()) return 0L;

    try {
      return Long.parseLong(len);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  public static String getApi(RoutingContext ctx) {
    String mount = ctx.mountPoint() != null ? ctx.mountPoint() : "";
    Route route = ctx.currentRoute();

    if (route != null && route.getPath() != null) {
      return normalizeTemplate(mount + route.getPath());
    }

    // Fallback: at least return actual path
    return ctx.request().path();
  }

  private static String normalizeTemplate(String path) {
    return path.replaceAll(":([A-Za-z0-9_]+)", "\\{$1\\}");
  }

  public static String getHttpMethod(RoutingContext ctx) {
    return ctx.request().method().name();
  }

  public static LocalDateTime now() {
    return LocalDateTime.now();
  }

  public static Long epochMs() {
    return System.currentTimeMillis();
  }
}
