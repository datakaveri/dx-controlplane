package org.cdpg.dx.aaa.leaderboard.service.impl;

import org.cdpg.dx.aaa.leaderboard.model.*;
import org.cdpg.dx.aaa.leaderboard.service.LeaderboardService;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.aaa.leaderboard.dao.LeaderboardDao;
import io.vertx.core.Future;

public class LeaderboardServiceImpl implements LeaderboardService {
  private final LeaderboardDao dao;

  public LeaderboardServiceImpl(LeaderboardDao dao) {
    this.dao = dao;
  }

  @Override
  public Future<LeaderboardResponse<OrganizationLeaderboardEntry>> getOrgLeaderboard(
      PaginatedRequest request) {
    return dao.fetchOrgLeaderboard(request);
  }

  @Override
  public Future<LeaderboardResponse<ProviderLeaderboardEntry>> getProviderLeaderboard(
      PaginatedRequest request) {
    return dao.fetchProviderLeaderboard(request);
  }

  @Override
  public Future<LeaderboardResponse<AssetLeaderboardEntry>> getAssetLeaderboard(
      PaginatedRequest request) {
    return dao.fetchAssetLeaderboard(request);
  }
}
