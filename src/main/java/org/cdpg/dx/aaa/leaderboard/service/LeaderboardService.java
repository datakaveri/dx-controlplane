package org.cdpg.dx.aaa.leaderboard.service;

import org.cdpg.dx.aaa.leaderboard.model.AssetLeaderboardEntry;
import org.cdpg.dx.aaa.leaderboard.model.OrganizationLeaderboardEntry;
import org.cdpg.dx.aaa.leaderboard.model.ProviderLeaderboardEntry;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardResponse;
import io.vertx.core.Future;

public interface LeaderboardService {
  Future<LeaderboardResponse<OrganizationLeaderboardEntry>> getOrgLeaderboard(
      PaginatedRequest request);

  Future<LeaderboardResponse<ProviderLeaderboardEntry>> getProviderLeaderboard(
      PaginatedRequest request);

  Future<LeaderboardResponse<AssetLeaderboardEntry>> getAssetLeaderboard(PaginatedRequest request);
}
