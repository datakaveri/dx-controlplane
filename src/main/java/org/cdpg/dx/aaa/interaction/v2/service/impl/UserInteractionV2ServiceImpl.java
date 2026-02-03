package org.cdpg.dx.aaa.interaction.v2.service.impl;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.interaction.v2.dao.UserInteractionV2Dao;
import org.cdpg.dx.aaa.interaction.v2.model.*;
import org.cdpg.dx.aaa.interaction.v2.service.UserInteractionV2Service;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.elastic.model.BulkSyncResult;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public class UserInteractionV2ServiceImpl implements UserInteractionV2Service {
  private static final Logger LOGGER = LogManager.getLogger(UserInteractionV2ServiceImpl.class);

  private final UserInteractionV2Dao dao;
  private final ItemService itemService;

  public UserInteractionV2ServiceImpl(UserInteractionV2Dao dao, ItemService itemService) {
    this.dao = dao;
    this.itemService = itemService;
  }

  @Override
  public Future<InteractionDelta> SaveIteraction(UUID userId, UserInteractionV2Request req) {
    return dao.upsertInteractionWithDelta(
            userId, req.assetId(), req.assetType().name(), req.action().name())
        .onSuccess(delta -> applyDeltaAsync(req, delta));
  }

  private void applyDeltaAsync(UserInteractionV2Request req, InteractionDelta delta) {
    EngagementDelta engagementDelta = computeEngagementDelta(delta);

    if (engagementDelta.isNoOp()) {
      return;
    }

    itemService
        .updateEngagementCounters(
            UUID.fromString(delta.entityId()),
            engagementDelta.likeDelta(),
            engagementDelta.dislikeDelta())
        .onFailure(
            err -> LOGGER.warn("Failed to update CAT metrics for asset={}", delta.entityId(), err));
  }

  private EngagementDelta computeEngagementDelta(InteractionDelta d) {
    int likeDelta = 0;
    int dislikeDelta = 0;

    if (d.oldLiked() != d.newLiked()) {
      likeDelta += d.newLiked() ? 1 : -1;
    }

    if (d.oldDisliked() != d.newDisliked()) {
      dislikeDelta += d.newDisliked() ? 1 : -1;
    }

    return new EngagementDelta(likeDelta, dislikeDelta);
  }

  @Override
  public Future<BulkSyncResult> syncInteractionMetrics() {

    return dao.aggregateInteractions().compose(itemService::bulkSyncMetrics);
  }

  private record EngagementDelta(int likeDelta, int dislikeDelta) {
    boolean isNoOp() {
      return likeDelta == 0 && dislikeDelta == 0;
    }
  }

  @Override
  public Future<PaginatedResult<InteractionRow>> getUserInteractions(PaginatedRequest request) {
    return dao.getUserInteractions(request).compose(this::enrichWithItemData);
  }

  private Future<PaginatedResult<InteractionRow>> enrichWithItemData(
      PaginatedResult<InteractionRow> page) {

    List<InteractionRow> rows = page.data();

    if (rows.isEmpty()) {
      return Future.succeededFuture(page);
    }

    List<Future<InteractionRow>> futures =
        rows.stream()
            .map(row -> fetchItemSummary(UUID.fromString(row.getAssetId())).map(row::withItem))
            .toList();

    return Future.all(futures).map(v -> page);
  }

  private Future<ItemSummary> fetchItemSummary(UUID assetId) {

    GetItemRequest request = new GetItemRequest(assetId.toString(), null);

    return itemService
        .getItem(request)
        .map(
            response -> {
              if (response == null || response.getTotalHits() == 0) {
                return null;
              }

              JsonObject item = response.getResponse().getJsonArray("results").getJsonObject(0);

              String type =
                  item.getJsonArray("type") != null && !item.getJsonArray("type").isEmpty()
                      ? item.getJsonArray("type").getString(0)
                      : null;

              return new ItemSummary(
                  item.getString("id"),
                  item.getString("name"),
                  item.getString("shortDescription"),
                  type,
                  item.getString("accessPolicy"),
                  item.getString("ownerUserId"),
                  item.getString("ownerUserName", ""),
                  item.getString("organizationId"),
                  item.getString("organization"),
                  item.getJsonObject("metrics", new JsonObject()));
            })
        .recover(
            err -> {
              LOGGER.warn("Failed to fetch item metadata for assetId={}", assetId, err);
              return Future.succeededFuture(null);
            });
  }
}
