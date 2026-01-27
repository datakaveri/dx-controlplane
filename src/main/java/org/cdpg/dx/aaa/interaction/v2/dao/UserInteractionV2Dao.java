package org.cdpg.dx.aaa.interaction.v2.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionDelta;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionRow;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.UUID;

public interface UserInteractionV2Dao {
  Future<PaginatedResult<InteractionRow>> getUserInteractions(PaginatedRequest paginatedRequest);

  Future<InteractionDelta> upsertInteractionWithDelta(
      UUID userId, UUID assetId, String assetType, String action);
}
