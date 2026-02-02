package org.cdpg.dx.aaa.interaction.dao;

import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.interaction.enums.ActionType;
import org.cdpg.dx.aaa.interaction.model.InteractionAggregate;
import org.cdpg.dx.aaa.interaction.model.UserInteraction;
import org.cdpg.dx.aaa.interaction.model.UserInteractionsPaginatedResponse;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.UpsertResult;

public interface UserInteractionDao {

  Future<UpsertResult<UserInteraction>> upsert(UserInteraction interaction);

  Future<Void> delete(UUID userId, UUID entityId, String actionType);

  Future<UserInteractionsPaginatedResponse> fetchUserInteractions(PaginatedRequest request);

  Future<UserInteraction> fetchExistingInteraction(
      UUID userId,
      UUID entityId,
      ActionType actionType
  );
  Future<List<InteractionAggregate>> aggregateInteractions();
}
