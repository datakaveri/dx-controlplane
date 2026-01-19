package org.cdpg.dx.aaa.leaderboard.dao;

import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardResponse;
import org.cdpg.dx.aaa.leaderboard.model.OrganizationLeaderboardEntry;
import org.cdpg.dx.aaa.leaderboard.model.ProviderLeaderboardEntry;
import org.cdpg.dx.aaa.leaderboard.model.AssetLeaderboardEntry;
import io.vertx.core.Future;

public interface LeaderboardDao {
    Future<LeaderboardResponse<OrganizationLeaderboardEntry>> fetchOrgLeaderboard(PaginatedRequest request);

    Future<LeaderboardResponse<ProviderLeaderboardEntry>> fetchProviderLeaderboard(PaginatedRequest request);

    Future<LeaderboardResponse<AssetLeaderboardEntry>> fetchAssetLeaderboard(PaginatedRequest request);
}
