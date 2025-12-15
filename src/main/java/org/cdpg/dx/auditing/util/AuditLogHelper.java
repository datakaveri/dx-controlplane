package org.cdpg.dx.auditing.util;

import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.auditing.enums.HttpMethod;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auth.authorization.model.DxRole;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public final class AuditLogHelper {

  private AuditLogHelper() {
    // utility class
  }

  /**
   * Creates a base ActivityAuditLog.Builder with: - user identity (user_id, role, delegation info)
   * - request metadata (ip, agent, size) - api path + http method - issuer - timestamps (created_at
   * + epoch_ms)
   *
   * <p>Entity information and action/origin must be set in handlers.
   */
  public static ActivityAuditLogBuilder.Builder createBaseAudit(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = AuditContextExtractor.getUserId(ctx);
    DxRole role = AuditContextExtractor.getUserRole(user);

    boolean isDelegate = AuditContextExtractor.isDelegate(user);
    UUID delegatorId = AuditContextExtractor.getDelegatorId(user);
    DxRole delegatorRole = AuditContextExtractor.getDelegatorRole(user);

    String ip = AuditContextExtractor.getClientIp(ctx);
    String userAgent = AuditContextExtractor.getUserAgent(ctx);

    String api = AuditContextExtractor.getApi(ctx);
    String methodStr = AuditContextExtractor.getHttpMethod(ctx);
    HttpMethod method = HttpMethod.valueOf(methodStr);

    String issuer = AuditContextExtractor.getIssuer(user);

    LocalDateTime now = AuditContextExtractor.now();
    Long epoch = AuditContextExtractor.epochMs();

    ActivityAuditLogBuilder.Builder builder = new ActivityAuditLogBuilder.Builder();

    builder.withId(UUID.randomUUID());

    // ---- User context ----
    builder.withUserId(userId);
    builder.withRole(role);
    builder.withIsDelegate(isDelegate);
    builder.withDelegatorId(delegatorId);
    builder.withDelegatorRole(delegatorRole);

    // ---- Request metadata ----
    builder.withApi(api);
    builder.withMethod(method);
    builder.withIp(ip);
    builder.withUserAgent(userAgent);
    builder.withIssuer(issuer);

    // ---- Timestamps ----
    builder.withCreatedAt(now.toString());
    builder.withEpochMs(epoch);

    // Status default – can be overridden by handler
    builder.withStatus("SUCCESS");
    builder.withStatusMessage("SUCCESS");
    builder.withMyActivityEnabled(true);

    return builder;
  }
}
