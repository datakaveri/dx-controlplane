package org.cdpg.dx.aaa.item.util;

import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_APPS;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_DATA_BANK;
import static org.cdpg.dx.aaa.common.Constants.NAME;
import static org.cdpg.dx.aaa.common.Constants.TYPE;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.item.enums.ItemAuditOperation;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.util.AuditLogHelper;

public final class ItemAuditLogHelper {
  private static final Map<String, String> ASSET_TYPE_MAPPING = Map.of(
      ITEM_TYPE_DATA_BANK, "DATABANK",
      ITEM_TYPE_AI_MODEL, "AI_MODEL",
      ITEM_TYPE_APPS, "USECASE"
  );

  private ItemAuditLogHelper() {}

  /**
   * Builds item audit log with variable operation.
   *
   * <p>Only `operation` changes. Everything else remains same.
   */
  public static UserActivityAuditLogBuilder buildItemAudit(
      RoutingContext ctx, JsonObject itemJson, ItemAuditOperation operation) {

    UserActivityAuditLogBuilder.Builder builder = AuditLogHelper.createBaseAudit(ctx)
        .withLogType("ASSET")
        .withOriginServer("CATALOGUE")
        .withAction(operation.value())
        .withAssetId(safeUuid(itemJson.getString(ID)));

    if (ItemAuditOperation.DELETE.equals(operation)) {
      String rawType = itemJson.getJsonArray(TYPE).getString(0);
      String type = ASSET_TYPE_MAPPING.getOrDefault(rawType, "UNKNOWN");

      builder
          .withAssetName(itemJson.getString(NAME))
          .withAssetType(type);
    }

    return builder.build();
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
