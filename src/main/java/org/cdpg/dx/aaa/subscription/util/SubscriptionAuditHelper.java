package org.cdpg.dx.aaa.subscription.util;

import static org.cdpg.dx.aaa.common.Constants.*;

import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.util.AuditLogHelper;

public class SubscriptionAuditHelper {

  private SubscriptionAuditHelper() {}

  private static final String CREATE_SUBSCRIPTION = "Create subscription";
  private static final String UPDATE_SUBSCRIPTION = "Update subscription";
  private static final String Delete_SUBSCRIPTION = "Delete subscription";
  private static final String VIEW_SUBSCRIPTION = "View subscription";
  private static final String LIST_SUBSCRIPTION = "List subscription";

  /** Builds Subscription audit log with variable assetId. */
  public static UserActivityAuditLogBuilder buildCreateSubscriptionAudit(
      RoutingContext ctx, String assetId) {

    UserActivityAuditLogBuilder.Builder builder =
        AuditLogHelper.createBaseAudit(ctx)
            .withLogType("USER_ACTION")
            .withOriginServer("AAA")
            .withAction(CREATE_SUBSCRIPTION)
            .withAssetId(safeUuid(assetId));
    return builder.build();
  }

  public static UserActivityAuditLogBuilder buildUpdateSubscriptionAudit(
      RoutingContext ctx, String assetId) {

    UserActivityAuditLogBuilder.Builder builder =
        AuditLogHelper.createBaseAudit(ctx)
            .withLogType("USER_ACTION")
            .withOriginServer("AAA")
            .withAction(UPDATE_SUBSCRIPTION)
            .withAssetId(safeUuid(assetId));
    return builder.build();
  }

  public static UserActivityAuditLogBuilder buildUDeleteSubscriptionAudit(RoutingContext ctx) {

    UserActivityAuditLogBuilder.Builder builder =
        AuditLogHelper.createBaseAudit(ctx)
            .withLogType("USER_ACTION")
            .withOriginServer("AAA")
            .withAction(Delete_SUBSCRIPTION);

    return builder.build();
  }

  public static UserActivityAuditLogBuilder buildUViewSubscriptionAudit(RoutingContext ctx) {

    UserActivityAuditLogBuilder.Builder builder =
        AuditLogHelper.createBaseAudit(ctx)
            .withLogType("USER_ACTION")
            .withOriginServer("AAA")
            .withAction(VIEW_SUBSCRIPTION);
    return builder.build();
  }

  public static UserActivityAuditLogBuilder buildUListSubscriptionAudit(RoutingContext ctx) {

    UserActivityAuditLogBuilder.Builder builder =
        AuditLogHelper.createBaseAudit(ctx)
            .withLogType("USER_ACTION")
            .withOriginServer("AAA")
            .withAction(LIST_SUBSCRIPTION);
    return builder.build();
  }

  private static UUID safeUuid(String id) {
    try {
      return id == null ? null : UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
