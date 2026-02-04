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
   * Best-effort asset enrichment.
   *
   * <p>Rules: - If assetId is null → skip - Never fail audit pipeline - Log success once, failures
   * as WARN
   */
  public Future<ActivityAuditLogEntity> enrich(ActivityAuditLogEntity entity) {

    if (entity == null || entity.getAssetId() == null) {
      LOGGER.debug("Asset enrichment skipped (no assetId)");
      return Future.succeededFuture(entity);
    }

    LOGGER.debug(
        "Starting asset enrichment [assetId={}, userId={}]",
        entity.getAssetId(),
        entity.getUserId());

    String userId = entity.getUserId() != null ? entity.getUserId().toString() : null;

    GetItemRequest request = new GetItemRequest(entity.getAssetId().toString(), userId);

    return itemService
        .getItem(request)
        .map(item -> applyAssetInfo(entity, item.getResponse()))
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

  private ActivityAuditLogEntity applyAssetInfo(
      ActivityAuditLogEntity entity, JsonObject response) {

    JsonObject itemJson = response.getJsonArray("results").getJsonObject(0);

    // ---- Asset basics ----
    entity.setAssetName(itemJson.getString("name"));
    entity.setAssetSortDescription(itemJson.getString("shortDescription"));
    entity.setAssetType(AssetTypeUtil.extractAssetType(itemJson));
    entity.setAssetAccessPolicy(itemJson.getString("accessPolicy"));

    // ---- Asset organisation ----
    entity.setAssetOrgId(safeParse(itemJson.getString("organizationId"), "organizationId"));
    entity.setAssetOrgName(itemJson.getString("organization"));
    entity.setAssetOrgType(itemJson.getString("organizationType"));

    // ---- Provider ----
    entity.setAssetProviderId(safeParse(itemJson.getString("ownerUserId"), "ownerUserId"));
    entity.setAssetProviderName(itemJson.getString("ownerUserName"));

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
