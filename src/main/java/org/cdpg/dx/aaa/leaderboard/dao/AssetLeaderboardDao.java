package org.cdpg.dx.aaa.leaderboard.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;

import java.util.UUID;

public interface AssetLeaderboardDao {

  Future<Void> upsert(LeaderboardEvent e);

  Future<Void> incrementViews(UUID assetId);

  Future<Void> incrementDownloads(UUID assetId);

  Future<Void> incrementLikes(UUID assetId);

  Future<Void> delete(UUID assetId);
}
