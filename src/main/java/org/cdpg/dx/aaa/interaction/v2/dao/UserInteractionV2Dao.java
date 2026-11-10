package org.cdpg.dx.aaa.interaction.v2.dao;

import io.vertx.core.Future;
import java.util.List;
import org.cdpg.dx.aaa.interaction.enums.ActionType;
import org.cdpg.dx.aaa.interaction.model.InteractionAggregate;
import org.cdpg.dx.aaa.interaction.model.UserInteraction;
import org.cdpg.dx.aaa.interaction.v2.enums.InteractionAction;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionDelta;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionRow;
import org.cdpg.dx.aaa.vote.model.VoteType;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.UUID;

public interface UserInteractionV2Dao {
  Future<PaginatedResult<InteractionRow>> getUserInteractions(PaginatedRequest paginatedRequest);

  Future<InteractionDelta> upsertInteractionWithDelta(
      UUID userId, UUID assetId, String assetType, String action);

  Future<List<InteractionAggregate>> aggregateInteractions();
}
