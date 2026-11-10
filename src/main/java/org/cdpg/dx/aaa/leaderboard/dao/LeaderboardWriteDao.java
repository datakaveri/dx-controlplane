package org.cdpg.dx.aaa.leaderboard.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;

public interface LeaderboardWriteDao {

  /** Upserts the asset row; resolves to {@code true} only when the asset was newly inserted. */
  Future<Boolean> upsertAssetOnCreate(LeaderboardEvent event);

  Future<Void> incrementAssetView(LeaderboardEvent event);

  Future<Void> incrementAssetDownload(LeaderboardEvent event);

  Future<Void> incrementAssetLike(LeaderboardEvent event);

  /** Upserts the provider row; bumps published counters only when {@code newAsset} is true. */
  Future<Void> upsertProviderOnCreate(LeaderboardEvent event, boolean newAsset);

  Future<Void> incrementProviderView(LeaderboardEvent event);

  Future<Void> incrementProviderDownload(LeaderboardEvent event);

  Future<Void> incrementProviderLike(LeaderboardEvent event);

  /** Upserts the organization row; bumps published counters only when {@code newAsset} is true. */
  Future<Void> upsertOrganizationOnCreate(LeaderboardEvent event, boolean newAsset);

  Future<Void> incrementOrganizationView(LeaderboardEvent event);

  Future<Void> incrementOrganizationDownload(LeaderboardEvent event);

  Future<Void> incrementOrganizationLike(LeaderboardEvent event);

  Future<Void> decrementAssetLike(LeaderboardEvent event);

  Future<Void> decrementProviderLike(LeaderboardEvent event);

  Future<Void> decrementOrganizationLike(LeaderboardEvent event);

  public Future<Void> deleteAssetAndAdjustLeaderboards(LeaderboardEvent e);
}
