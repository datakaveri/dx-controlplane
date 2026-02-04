package org.cdpg.dx.aaa.interaction.v2.service;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.interaction.v2.model.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.response.PaginatedApiResponse;
import org.cdpg.dx.database.elastic.model.BulkSyncResult;

public interface UserInteractionV2Service {

  Future<PaginatedApiResponse<UserInteractionAssetResponse>> getUserInteractions(
      PaginatedRequest request);

  Future<InteractionDelta> saveInteraction(UUID userId, UserInteractionV2Request req);

  Future<BulkSyncResult> syncInteractionMetrics();
}
