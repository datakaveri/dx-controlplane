package org.cdpg.dx.aaa.interaction.v2.dao;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.interaction.v2.model.FeedbackStatus;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedbackPaginatedResponse;
import org.cdpg.dx.common.request.PaginatedRequest;

public interface UserFeedbackDao {

  Future<Boolean> deleteFeedback(UUID userId, UUID assetId);

  Future<UserFeedbackPaginatedResponse> fetchUserFeedbacks(PaginatedRequest request);

  Future<UserFeedbackPaginatedResponse> fetchPlatformUsersFeedbacks(PaginatedRequest request);

  Future<UserFeedback> postFeedback(UserFeedback request);

  Future<UserFeedback> putFeedback(UserFeedback request);

  Future<UserFeedback> updateFeedbackStatus(UUID feedbackId, FeedbackStatus status, String comment);

  Future<UserFeedbackPaginatedResponse> fetchApprovedPlatformUserFeedbacks(
      PaginatedRequest request);
}
