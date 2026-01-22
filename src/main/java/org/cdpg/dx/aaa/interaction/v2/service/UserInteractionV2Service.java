package org.cdpg.dx.aaa.interaction.v2.service;

import io.vertx.core.Future;
import java.util.UUID;

import org.cdpg.dx.aaa.interaction.v2.model.InteractionDelta;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionRow;
import org.cdpg.dx.aaa.interaction.v2.model.UserInteractionV2Request;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public interface UserInteractionV2Service {
  Future<PaginatedResult<InteractionRow>> getUserInteractions(PaginatedRequest paginatedRequest);

  Future<InteractionDelta> SaveIteraction(UUID userId, UserInteractionV2Request req);
}
