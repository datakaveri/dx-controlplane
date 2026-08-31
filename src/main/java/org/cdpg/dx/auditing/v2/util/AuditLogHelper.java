package org.cdpg.dx.auditing.v2.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;

public final class AuditLogHelper {

  private AuditLogHelper() {
    // utility class
  }

  /**
   * Creates a base UserActivityAuditLogBuilder.Builder with: - user identity (user_id, name, role)
   * - organisation context (org_id, org_name) - request metadata (ip, user-agent) - api template +
   * http method - issuer - timestamps
   *
   * <p>Asset / workflow / log_type must be set by caller.
   */
  public static UserActivityAuditLogBuilder.Builder createBaseAudit(RoutingContext ctx) {

    User user = ctx.user();

    // --------------------
    // Identity
    // --------------------
    UUID userId = AuditContextExtractor.getUserId(ctx);
    String userName = AuditContextExtractor.getUserName(user);
    String role = AuditContextExtractor.getEffectiveRole(user);
    String issuer = AuditContextExtractor.getIssuer(user);

    // --------------------
    // Delegation (userId above is already the delegator/primary user when delegated;
    // delegateeId captures who actually made the call - null means not a delegated request)
    // --------------------
    UUID delegateeId = AuditContextExtractor.getDelegateeId(ctx);

    // --------------------
    // Organisation
    // --------------------
    UUID orgId = AuditContextExtractor.getOrgId(user);
    String orgName = AuditContextExtractor.getOrgName(user);
    // orgType intentionally NOT resolved here

    // --------------------
    // Request metadata
    // --------------------
    String apiTemplate = AuditContextExtractor.getApiTemplate(ctx);
    String httpMethod = AuditContextExtractor.getHttpMethod(ctx);
    String ip = AuditContextExtractor.getClientIp(ctx);
    String userAgent = AuditContextExtractor.getUserAgent(ctx);

    // --------------------
    // Time
    // --------------------
    String createdAt = AuditContextExtractor.now().toString();

    // --------------------
    // Builder
    // --------------------
    UserActivityAuditLogBuilder.Builder builder = new UserActivityAuditLogBuilder.Builder();

    builder
        .withId(UUID.randomUUID())

        // ---- User context ----
        .withUserId(userId)
        .withUserName(userName)
        .withRole(role)
        .withIssuer(issuer)

        // ---- Delegation ----
        .withDelegateeId(delegateeId)

        // ---- Org context ----
        .withOrgId(orgId)
        .withOrgName(orgName)

        // ---- API context ----
        .withApi(apiTemplate)
        .withHttpMethod(httpMethod)

        // ---- Request metadata ----
        .withIpAddress(ip)
        .withUserAgent(userAgent)

        // ---- Time ----
        .withCreatedAt(createdAt)

        // ---- Defaults (safe) ----
        .withContext(new JsonObject());

    return builder;
  }
}
