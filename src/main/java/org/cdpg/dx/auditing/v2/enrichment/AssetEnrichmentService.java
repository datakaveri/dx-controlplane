package org.cdpg.dx.auditing.v2.enrichment;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.auditing.v2.util.AssetTypeUtil;

import java.util.UUID;

public class AssetEnrichmentService {

  private static final Logger LOGGER = LogManager.getLogger(AssetEnrichmentService.class);

  private final ItemService itemService;

  public AssetEnrichmentService(ItemService itemService) {
    this.itemService = itemService;
  }

  /**
   * Best-effort asset enrichment.
   *
   * <p>Rules: - If assetId is null → skip - Never fail audit pipeline - Log success once, failures
   * as WARN
   */
  public Future<ActivityAuditLogEntity> enrich(ActivityAuditLogEntity entity) {

    if (entity == null
        || entity.getAssetId() == null
        || entity.getAction().equalsIgnoreCase("DELETE")
        || entity.getAction().equalsIgnoreCase("CREATE")) {
      // CREATE and DELETE asset fields are populated at source (ItemAuditLogHelper); skipping the
      // Elasticsearch lookup here avoids racing with index refresh on freshly-created items.
      LOGGER.debug("Asset enrichment skipped (no assetId, or create/delete populated at source)");
      return Future.succeededFuture(entity);
    }

    LOGGER.debug("Starting asset enrichment [assetId={}]", entity.getAssetId());

    return itemService
        .getItemSource(entity.getAssetId().toString())
        .map(source -> applyAssetInfo(entity, source))
        .onSuccess(
            e ->
                LOGGER.info(
                    "Asset enrichment successful [assetId={}, assetName={}, assetType={}]",
                    e.getAssetId(),
                    e.getAssetName(),
                    e.getAssetType()))
        .recover(
            err -> {
              LOGGER.warn(
                  "Asset enrichment failed [assetId={}]. Proceeding without enrichment",
                  entity.getAssetId(),
                  err);
              return Future.succeededFuture(entity);
            });
  }

  private ActivityAuditLogEntity applyAssetInfo(ActivityAuditLogEntity entity, JsonObject source) {

    if (source == null) {
      LOGGER.warn(
          "Asset enrichment found no item for assetId={}; proceeding without asset details",
          entity.getAssetId());
      return entity;
    }

    // ---- Asset basics ----
    entity.setAssetName(source.getString("name"));
    entity.setAssetSortDescription(source.getString("shortDescription"));
    entity.setAssetType(AssetTypeUtil.extractAssetType(source));
    entity.setAssetAccessPolicy(source.getString("accessPolicy"));

    // ---- Asset organisation ----
    entity.setAssetOrgId(safeParse(source.getString("organizationId"), "organizationId"));
    entity.setAssetOrgName(source.getString("organization"));
    entity.setAssetOrgType(source.getString("organizationType"));

    // ---- Provider ----
    entity.setAssetProviderId(safeParse(source.getString("ownerUserId"), "ownerUserId"));
    entity.setAssetProviderName(source.getString("ownerUserName"));

    return entity;
  }

  private static UUID safeParse(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Invalid UUID for field {}: {}", fieldName, value);
      return null;
    }
  }
}
