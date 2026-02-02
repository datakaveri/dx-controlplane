package org.cdpg.dx.aaa.item.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.util.AuditLogHelper;
import org.cdpg.dx.aaa.item.enums.ItemAuditOperation;

import java.util.UUID;

public final class ItemAuditLogHelper {

  private ItemAuditLogHelper() {}

  /**
   * Builds item audit log with variable operation.
   *
   * <p>Only `operation` changes. Everything else remains same.
   */
  public static UserActivityAuditLogBuilder buildItemAudit(
      RoutingContext ctx, JsonObject itemJson, ItemAuditOperation operation) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withLogType("ASSET")
        .withOriginServer("CATALOGUE")
        .withAction(operation.value())
        .withAssetId(safeUuid(itemJson.getString("id")))
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
