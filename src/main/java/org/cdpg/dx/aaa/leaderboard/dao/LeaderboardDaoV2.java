package org.cdpg.dx.aaa.leaderboard.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;

public interface LeaderboardDaoV2 {

  Future<Void> upsertAssetOnCreate(LeaderboardEvent event);

  Future<Void> incrementAssetView(LeaderboardEvent event);

  Future<Void> incrementAssetDownload(LeaderboardEvent event);

  Future<Void> incrementAssetLike(LeaderboardEvent event);

  Future<Void> upsertProviderOnCreate(LeaderboardEvent event);

  Future<Void> incrementProviderView(LeaderboardEvent event);

  Future<Void> incrementProviderDownload(LeaderboardEvent event);

  Future<Void> incrementProviderLike(LeaderboardEvent event);

  Future<Void> upsertOrganizationOnCreate(LeaderboardEvent event);

  Future<Void> incrementOrganizationView(LeaderboardEvent event);

  Future<Void> incrementOrganizationDownload(LeaderboardEvent event);

  Future<Void> incrementOrganizationLike(LeaderboardEvent event);

  Future<Void> deleteAsset(LeaderboardEvent event);

  Future<Void> decrementAssetLike(LeaderboardEvent event);

  Future<Void> decrementProviderLike(LeaderboardEvent event);

  Future<Void> decrementOrganizationLike(LeaderboardEvent event);

  public Future<Void> deleteAssetAndAdjustLeaderboards(LeaderboardEvent e);
}
