package org.cdpg.dx.aaa.interaction.v2.service.impl;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.interaction.v2.dao.UserInteractionV2Dao;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionDelta;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionRow;
import org.cdpg.dx.aaa.interaction.v2.model.UserInteractionV2Request;
import org.cdpg.dx.aaa.interaction.v2.service.UserInteractionV2Service;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public class UserInteractionV2ServiceImpl implements UserInteractionV2Service {

  private final UserInteractionV2Dao dao;

  public UserInteractionV2ServiceImpl(UserInteractionV2Dao dao) {
    this.dao = dao;
  }

  @Override
  public Future<PaginatedResult<InteractionRow>> getUserInteractions(
      PaginatedRequest paginatedRequest) {
    return dao.getUserInteractions(paginatedRequest);
  }

  @Override
  public Future<InteractionDelta> SaveIteraction(UUID userId, UserInteractionV2Request req) {
    return dao.upsertInteractionWithDelta(
        userId, req.entityId(), req.entityType().name(), req.action().name());
  }
}
