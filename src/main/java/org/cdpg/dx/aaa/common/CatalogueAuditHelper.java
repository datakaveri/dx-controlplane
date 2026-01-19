package org.cdpg.dx.aaa.common;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.auditing.enums.Operation;
import org.cdpg.dx.auditing.enums.OriginServer;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.util.AuditLogHelper;

/**
 * CatalogueAuditHelper
 *
 * <p>Responsible ONLY for building audit logs for Catalogue domain. Does NOT publish or persist
 * logs.
 */
public final class CatalogueAuditHelper {

  private CatalogueAuditHelper() {
    // utility class
  }

  /** Build audit log for catalogue item actions. */
  public static ActivityAuditLogBuilder buildItemAudit(
      RoutingContext ctx, Operation operation, JsonObject itemJson) {

    String orgIdStr = itemJson.getString("organizationId");
    UUID orgId = orgIdStr != null ? UUID.fromString(orgIdStr) : null;

    UUID entityId = UUID.fromString(itemJson.getString("id"));

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrgId(orgId)
        .withOrgName(itemJson.getString("organization", itemJson.getString("department")))
        .withProviderId(UUID.fromString(itemJson.getString("ownerUserId")))
        .withOrigin(OriginServer.CATALOGUE)
        .withOperation(operation)
        .withEntityType(resolveEntityType(itemJson))
        .withEntityId(entityId)
        .withEntityName(itemJson.getString("name"))
        .withShortDescription(itemJson.getString("shortDescription"))
        .withMyActivityEnabled(true)
        .build();
  }

  /** Resolve catalogue entity type from item JSON. */
  private static EntityType resolveEntityType(JsonObject itemJson) {
    JsonArray typeArray = itemJson.getJsonArray("type", new JsonArray());

    for (Object value : typeArray) {
      if (value == null) continue;
      try {
        ItemType itemType = ItemType.fromTypeValue(value.toString());
        return mapToEntityType(itemType);
      } catch (IllegalArgumentException ignored) {
        // skip unknown types
      }
    }
    return null;
  }

  /** Map ItemType → Auditing EntityType. */
  private static EntityType mapToEntityType(ItemType type) {
    if (type == null) return null;

    return switch (type) {
      case AI_MODEL -> EntityType.AI_MODEL;
      case DATA_BANK -> EntityType.DATABANK;
      case APPS -> EntityType.APPS;
      default -> null;
    };
  }
}
