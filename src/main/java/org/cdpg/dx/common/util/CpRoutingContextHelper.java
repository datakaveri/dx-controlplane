package org.cdpg.dx.common.util;

import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;

/**
 * Control-plane-specific routing context helper for methods that depend on CP-specific types (e.g.
 * UserActivityAuditLogBuilder from auditing v2).
 */
public class CpRoutingContextHelper {

  private static final String AUDITING_LOG_V2 = "auditingLogV2";

  private CpRoutingContextHelper() {}

  public static Optional<List<UserActivityAuditLogBuilder>> getAuditingLogV2(
      RoutingContext routingContext) {
    return Optional.ofNullable(routingContext.get(AUDITING_LOG_V2));
  }

  public static void setAuditingLogV2(
      RoutingContext routingContext, UserActivityAuditLogBuilder auditingLog) {
    List<UserActivityAuditLogBuilder> logs =
        getAuditingLogV2(routingContext).orElseGet(ArrayList::new);
    logs.add(auditingLog);
    routingContext.put(AUDITING_LOG_V2, logs);
  }
}
