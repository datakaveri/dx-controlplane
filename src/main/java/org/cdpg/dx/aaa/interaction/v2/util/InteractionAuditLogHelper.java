package org.cdpg.dx.aaa.interaction.v2.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.aaa.interaction.v2.enums.InteractionAuditAction;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionDelta;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.util.AuditLogHelper;
import org.cdpg.dx.aaa.item.enums.ItemAuditOperation;

import java.util.UUID;

public final class InteractionAuditLogHelper {

  private InteractionAuditLogHelper() {}

  /**
   * Builds item audit log with variable operation.
   *
   * <p>Only `operation` changes. Everything else remains same.
   */
  public static UserActivityAuditLogBuilder buildItemAudit(
      RoutingContext ctx, String id, InteractionAuditAction action, InteractionDelta delta) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withLogType("USER_ACTION")
        .withOriginServer("AAA")
        .withAction(action.value())
        .withAssetId(safeUuid(id))
        // The leaderboard only tracks likes, so a Dislike/Neutral event must say whether it
        // removed an existing like: the action alone can't distinguish LIKE→DISLIKE (likes -1)
        // from none→DISLIKE (likes unchanged).
        .withContext(delta == null ? null : delta.toJson())
        .build();
  }

  // ------------------------
  // Helpers
  // ------------------------

  private static UUID safeUuid(String id) {
    try {
      return id == null ? null : UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
