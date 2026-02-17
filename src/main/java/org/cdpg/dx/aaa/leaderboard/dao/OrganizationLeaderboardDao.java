package org.cdpg.dx.aaa.leaderboard.dao;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;

public interface OrganizationLeaderboardDao {

  Future<Void> ensureExists(LeaderboardEvent e);

  Future<Void> incrementPublished(LeaderboardEvent e);

  Future<Void> decrementPublished(LeaderboardEvent e);

  Future<Void> incrementViews(UUID organizationId);

  Future<Void> incrementDownloads(UUID organizationId);

  Future<Void> incrementLikes(UUID organizationId);
}
