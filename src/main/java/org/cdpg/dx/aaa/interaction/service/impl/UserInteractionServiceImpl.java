package org.cdpg.dx.aaa.interaction.service.impl;

import io.vertx.core.Future;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.elastic.model.BulkSyncResult;
import org.cdpg.dx.aaa.interaction.dao.UserInteractionDao;
import org.cdpg.dx.aaa.interaction.enums.ActionType;
import org.cdpg.dx.aaa.interaction.enums.InteractionValue;
import org.cdpg.dx.aaa.interaction.model.*;
import org.cdpg.dx.aaa.interaction.service.UserInteractionService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.common.request.PaginatedRequest;

public class UserInteractionServiceImpl implements UserInteractionService {

  private static final Logger LOGGER = LogManager.getLogger(UserInteractionServiceImpl.class);

  private final UserInteractionDao dao;
  private final ItemService itemService;

  public UserInteractionServiceImpl(UserInteractionDao dao,
                                    ItemService itemService) {
    this.dao = dao;
    this.itemService = itemService;
  }

  @Override
  public Future<Void> handleInteraction(UUID userId, InteractionRequest req) {

    return dao
        .fetchExistingInteraction(userId, req.entityId(), req.actionType())
        .compose(existing -> {

          InteractionValue oldValue =
              existing != null ? existing.value() : null;

          InteractionValue newValue =
              req.value() == InteractionValue.REMOVE ? null : req.value();

          EngagementDelta delta =
              (req.actionType() == ActionType.VOTE)
                  ? computeVoteDelta(oldValue, newValue)
                  : new EngagementDelta(0, 0);

          Future<Void> persistence =
              (newValue == null)
                  ? dao.delete(userId, req.entityId(), req.actionType().name())
                  : dao.upsert(
                  new UserInteraction(
                      existing != null ? existing.id() : null,
                      userId,
                      req.entityId(),
                      req.entityType(),
                      req.actionType(),
                      newValue
                  )
              ).mapEmpty();

          return persistence.onSuccess(v -> applyDeltaAsync(req, delta));
        });
  }

  private void applyDeltaAsync(
      InteractionRequest req,
      EngagementDelta delta
  ) {
    if (delta.isNoOp()) return;

    itemService
        .updateEngagementCounters(
            req.entityId(),
            delta.likeDelta(),
            delta.dislikeDelta()
        )
        .onFailure(err ->
            LOGGER.warn(
                "Failed to update CAT metrics for entity={}",
                req.entityId(),
                err
            )
        );
  }

  @Override
  public Future<UserInteractionsPaginatedResponse> getUserInteractions(PaginatedRequest request) {
    LOGGER.debug("UserInteractionsPaginatedResponse() method started");
    return dao.fetchUserInteractions(request);
  }

  private record EngagementDelta(int likeDelta, int dislikeDelta) {
    boolean isNoOp() {
      return likeDelta == 0 && dislikeDelta == 0;
    }
  }

  private EngagementDelta computeVoteDelta(
      InteractionValue oldValue,
      InteractionValue newValue
  ) {
    int like = 0;
    int dislike = 0;

    if (oldValue == newValue) {
      return new EngagementDelta(0, 0);
    }

    // remove old vote
    if (oldValue == InteractionValue.LIKE) like--;
    if (oldValue == InteractionValue.DISLIKE) dislike--;

    // add new vote
    if (newValue == InteractionValue.LIKE) like++;
    if (newValue == InteractionValue.DISLIKE) dislike++;

    return new EngagementDelta(like, dislike);
  }

  @Override
  public Future<BulkSyncResult> syncInteractionMetrics() {

    return dao
        .aggregateInteractions()
        .compose(itemService::bulkSyncMetrics);
  }


}
