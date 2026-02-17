package org.cdpg.dx.aaa.leaderboard.dao;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;

public interface ProviderLeaderboardDao {

  Future<Void> ensureExists(LeaderboardEvent e);

  Future<Void> incrementPublished(LeaderboardEvent e);

  Future<Void> decrementPublished(LeaderboardEvent e);

  Future<Void> incrementViews(UUID providerId);

  Future<Void> incrementDownloads(UUID providerId);

  Future<Void> incrementLikes(UUID providerId);
}
