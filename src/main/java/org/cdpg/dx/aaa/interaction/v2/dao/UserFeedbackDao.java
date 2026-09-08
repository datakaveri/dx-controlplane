package org.cdpg.dx.aaa.interaction.v2.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.interaction.v2.model.ProviderFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.ProviderFeedbackPaginatedResponse;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedbackPaginatedResponse;
import org.cdpg.dx.common.request.PaginatedRequest;

import java.util.UUID;

public interface UserFeedbackDao {

  Future<Boolean> deleteFeedback(UUID userId, UUID assetId);

  Future<UserFeedbackPaginatedResponse> fetchUserFeedbacks(PaginatedRequest request);


  Future<UserFeedback> postFeedback(UserFeedback request);
}
