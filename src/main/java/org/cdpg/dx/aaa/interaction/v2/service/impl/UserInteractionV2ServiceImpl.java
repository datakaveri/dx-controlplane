package org.cdpg.dx.aaa.interaction.v2.service.impl;

import io.vertx.core.Future;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.interaction.v2.dao.UserInteractionV2Dao;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionDelta;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionRow;
import org.cdpg.dx.aaa.interaction.v2.model.UserInteractionV2Request;
import org.cdpg.dx.aaa.interaction.v2.service.UserInteractionV2Service;
import org.cdpg.dx.aaa.item.service.ItemService;
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
  public Future<PaginatedResult<InteractionRow>> getUserInteractions(
      PaginatedRequest paginatedRequest) {
    return dao.getUserInteractions(paginatedRequest);
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
}
