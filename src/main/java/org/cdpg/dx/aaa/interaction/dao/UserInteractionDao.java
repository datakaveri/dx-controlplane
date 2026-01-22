package org.cdpg.dx.aaa.interaction.dao;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.interaction.model.UserInteraction;
import org.cdpg.dx.aaa.interaction.model.UserInteractionsPaginatedResponse;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.UpsertResult;

public interface UserInteractionDao {

  Future<UpsertResult<UserInteraction>> upsert(UserInteraction interaction);

  Future<Void> delete(UUID userId, UUID entityId, String actionType);

  Future<UserInteractionsPaginatedResponse> fetchUserInteractions(PaginatedRequest request);
}
