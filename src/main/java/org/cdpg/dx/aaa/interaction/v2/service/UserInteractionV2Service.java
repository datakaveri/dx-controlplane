package org.cdpg.dx.aaa.interaction.v2.service;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.interaction.v2.enums.ProviderFeedbackType;
import org.cdpg.dx.aaa.interaction.v2.model.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.response.PaginatedApiResponse;
import org.cdpg.dx.database.elastic.model.BulkSyncResult;

public interface UserInteractionV2Service {

  Future<PaginatedApiResponse<UserInteractionAssetResponse>> getUserInteractions(
      PaginatedRequest request);

  Future<InteractionDelta> saveInteraction(UUID userId, UserInteractionV2Request req);

  Future<BulkSyncResult> syncInteractionMetrics();

  Future<UserFeedback> postUserFeedback(UserFeedback request);

  Future<UserFeedback> putUserFeedback(UserFeedback request);

  Future<UserFeedback> updateFeedbackStatus(UUID feedbackId, FeedbackStatus status, String comment);

  Future<UserFeedbackPage> getUserFeedback(PaginatedRequest request);
  Future<UserFeedbackPage> getPlatformUsersFeedbacks(PaginatedRequest request);

  Future<Boolean> deleteUserFeedback(UUID userId, UUID assetId);

  Future<ProviderFeedback> postProviderFeedback(ProviderFeedback providerFeedback);

  Future<ProviderFeedbackPaginatedResponse> getProviderFeedback(PaginatedRequest request);

  Future<Boolean> deleteProviderFeedback(UUID userId, UUID assetId, ProviderFeedbackType type);

  Future<UserFeedbackPage> getApprovedPlatformUserFeedbacks(PaginatedRequest request);
}
