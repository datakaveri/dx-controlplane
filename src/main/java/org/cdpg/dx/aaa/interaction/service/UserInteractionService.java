package org.cdpg.dx.aaa.interaction.service;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.database.elastic.model.BulkSyncResult;
import org.cdpg.dx.aaa.interaction.model.InteractionRequest;
import org.cdpg.dx.aaa.interaction.model.UserInteractionsPaginatedResponse;
import org.cdpg.dx.common.request.PaginatedRequest;

public interface UserInteractionService {
  Future<Void> handleInteraction(UUID userId, InteractionRequest request);

  Future<UserInteractionsPaginatedResponse> getUserInteractions(PaginatedRequest request);

  Future<BulkSyncResult> syncInteractionMetrics();
}
