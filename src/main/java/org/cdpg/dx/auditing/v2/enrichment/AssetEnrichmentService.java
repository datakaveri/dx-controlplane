package org.cdpg.dx.auditing.v2.enrichment;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.auditing.v2.util.AssetTypeUtil;

import java.util.UUID;

public class AssetEnrichmentService {

  private static final Logger LOGGER = LogManager.getLogger(AssetEnrichmentService.class);

  private final ItemService itemService;

  public AssetEnrichmentService(ItemService itemService) {
    this.itemService = itemService;
  }

  /**
   * Enrich audit entity with asset-related data.
   *
   * <p>Rules: - If assetId is null → skip - If already enriched → skip - If item service fails →
   * return original entity - Audit must NEVER fail due to enrichment
   */
  public Future<ActivityAuditLogEntity> enrich(ActivityAuditLogEntity entity) {
    LOGGER.info("Starting asset enrichment for entity: {}", entity.getAssetId());

    if (entity == null || entity.getAssetId() == null) {
      return Future.succeededFuture(entity);
    }
    GetItemRequest getItemRequest =
        new GetItemRequest(entity.getAssetId().toString(), entity.getUserId().toString());
    // 3️⃣ Fetch from Item Service
    return itemService
        .getItem(getItemRequest)
        .map(
            item -> {

              // ---- Asset basics ----
              JsonObject itemJson = item.getResponse().getJsonArray("results").getJsonObject(0);
              entity.setAssetName(itemJson.getString("name"));
              LOGGER.debug("After setAssetName: {}", entity.getAssetName());
              entity.setAssetSortDescription(itemJson.getString("shortDescription"));
              entity.setAssetType(AssetTypeUtil.extractAssetType(itemJson));
              entity.setAssetAccessPolicy(itemJson.getString("accessPolicy"));

              // ---- Asset organisation ----
              entity.setAssetOrgId(
                  safeParse(itemJson.getString("organizationId"), "organizationId"));

              entity.setAssetOrgName(itemJson.getString("organization"));
              entity.setAssetOrgType(itemJson.getString("organizationType"));

              // ---- Provider ----
              entity.setAssetProviderId(
                  safeParse(itemJson.getString("ownerUserId"), "ownerUserId"));

              entity.setAssetProviderName(itemJson.getString("ownerUserName"));

              LOGGER.debug("Asset enrichment completed for assetId={}", entity.toJson());
              return entity;
            })
        .onFailure(
            err ->
                LOGGER.warn(
                    "Asset enrichment failed for assetId={}: {}",
                    entity.getAssetId(),
                    err.getMessage()))
        .recover(err -> Future.succeededFuture(entity));
  }

  public static UUID safeParse(String value, String fieldName) {
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
