package org.cdpg.dx.aaa.interaction.service.impl;

import io.vertx.core.Future;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.interaction.dao.UserInteractionDao;
import org.cdpg.dx.aaa.interaction.enums.InteractionValue;
import org.cdpg.dx.aaa.interaction.model.*;
import org.cdpg.dx.aaa.interaction.service.UserInteractionService;
import org.cdpg.dx.common.request.PaginatedRequest;

public class UserInteractionServiceImpl implements UserInteractionService {

  private static final Logger LOGGER = LogManager.getLogger(UserInteractionServiceImpl.class);

  private final UserInteractionDao dao;

  public UserInteractionServiceImpl(UserInteractionDao dao) {
    this.dao = dao;
  }

  @Override
  public Future<Void> handleInteraction(UUID userId, InteractionRequest req) {


    // REMOVE = delete row
    if (req.value() == InteractionValue.REMOVE) {
      LOGGER.info("Removing {} for user={} entity={}", req.actionType(), userId, req.entityId());

      return dao.delete(userId, req.entityId(), req.actionType().name());
    }
    LOGGER.info(
        "Upserting {}={} for user={} entity={}",
        req.actionType(),
        req.value(),
        userId,
        req.entityId());
    // UPSERT
    UserInteraction interaction =
        new UserInteraction(
            null, userId, req.entityId(), req.entityType(), req.actionType(), req.value());

    return dao.upsert(interaction).mapEmpty();
  }

  @Override
  public Future<UserInteractionsPaginatedResponse> getUserInteractions(PaginatedRequest request) {
    LOGGER.debug("UserInteractionsPaginatedResponse() method started");
    return dao.fetchUserInteractions(request);
  }
}
