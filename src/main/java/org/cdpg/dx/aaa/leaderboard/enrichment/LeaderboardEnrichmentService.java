package org.cdpg.dx.aaa.leaderboard.enrichment;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;
import org.cdpg.dx.auditing.v2.util.AssetTypeUtil;

import java.util.UUID;

public class LeaderboardEnrichmentService {

  private static final Logger LOGGER = LogManager.getLogger(LeaderboardEnrichmentService.class);

  private final ItemService itemService;

  public LeaderboardEnrichmentService(ItemService itemService) {
    this.itemService = itemService;
  }

  public Future<LeaderboardEvent> enrich(LeaderboardEvent event) {

    // No asset → nothing to enrich
    if (event == null || event.assetId() == null || event.action().equalsIgnoreCase("DELETE")) {
      return Future.succeededFuture(event);
    }

    GetItemRequest request = new GetItemRequest(event.assetId().toString(), null);

    return itemService
        .getItem(request)
        .map(resp -> applyItem(event, resp.getResponse()))
        .onSuccess(
            e ->
                LOGGER.info(
                    "Leaderboard enrichment success [assetId={}, publishStatus={}]",
                    e.assetId(),
                    e.publishStatus()))
        .recover(
            err -> {
              LOGGER.warn(
                  "Leaderboard enrichment failed [assetId={}], skipping eligibility",
                  event.assetId(),
                  err);
              return Future.succeededFuture(event);
            });
  }

  private LeaderboardEvent applyItem(LeaderboardEvent event, JsonObject response) {

    JsonObject item = response.getJsonArray("results").getJsonObject(0);

    boolean dataUploadStatus = true;

    String publishStatus = "ACTIVE";
    // Only CREATE / UPDATE need state check
    if (!event.action().equals("CREATE") && !event.action().equals("UPDATE")) {
      dataUploadStatus = item.getBoolean("dataUploadStatus", true);
      publishStatus = item.getString("publishStatus", "ACTIVE");
    }

    return new LeaderboardEvent(
        event.assetId(),
        event.action(),
        AssetTypeUtil.extractAssetType(item),
        safeUuid(item.getString("ownerUserId")),
        safeUuid(item.getString("organizationId")),
        item.getString("name"),
        item.getString("accessPolicy"),
        item.getString("organization"),
        item.getString("organizationType"),
        dataUploadStatus,
        publishStatus,
        event.createdAt());
  }

  private UUID safeUuid(String v) {
    try {
      return v == null ? null : UUID.fromString(v);
    } catch (Exception e) {
      return null;
    }
  }
}
