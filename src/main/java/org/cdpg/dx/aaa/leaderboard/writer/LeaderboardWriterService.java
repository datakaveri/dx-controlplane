package org.cdpg.dx.aaa.leaderboard.writer;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.leaderboard.dao.*;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;

public class LeaderboardWriterService {

  private static final Logger LOGGER = LogManager.getLogger(LeaderboardWriterService.class);
  private final LeaderboardDaoV2 dao;

  public LeaderboardWriterService(LeaderboardDaoV2 dao) {
    this.dao = dao;
  }

  public Future<Void> handle(LeaderboardEvent e) {
    LOGGER.debug(
        "Processing event: action={}, assetId={}, providerId={}, organizationId={}, publishStatus={}, dataUploadStatus={}",
        e.action(),
        e.assetId(),
        e.providerId(),
        e.organizationId(),
        e.publishStatus(),
        e.dataUploadStatus());
    if (!isEligible(e)) {
      LOGGER.info(
          "Event is not eligible for processing, skipping leaderboard updates [assetId={},"
              + " action={}]",
          e.assetId(),
          e.action());
      return Future.succeededFuture();
    }
    LOGGER.debug("Event is eligible for processing, proceeding with leaderboard updates");
    return switch (e.action().toUpperCase()) {
      case "CREATE", "UPDATE" ->
          dao.upsertAssetOnCreate(e)
              .compose(
                  newAsset -> {
                    if (newAsset) {
                      // Rare, meaningful transition — the counterpart of the deletion log
                      LOGGER.info(
                          "Asset entered leaderboard [assetId={}, assetType={}, providerId={},"
                              + " organizationId={}]",
                          e.assetId(),
                          e.assetType(),
                          e.providerId(),
                          e.organizationId());
                    }
                    return dao.upsertProviderOnCreate(e, newAsset)
                        .compose(v -> dao.upsertOrganizationOnCreate(e, newAsset));
                  });

      case "VIEW" ->
          dao.incrementAssetView(e)
              .compose(v -> dao.incrementProviderView(e))
              .compose(v -> dao.incrementOrganizationView(e));

      case "DOWNLOAD" ->
          dao.incrementAssetDownload(e)
              .compose(v -> dao.incrementProviderDownload(e))
              .compose(v -> dao.incrementOrganizationDownload(e));

      case "LIKE" ->
          dao.incrementAssetLike(e)
              .compose(v -> dao.incrementProviderLike(e))
              .compose(v -> dao.incrementOrganizationLike(e));
      // Only likes are tracked, so DISLIKE/NEUTRAL affect counters solely when they replace an
      // existing LIKE. none→DISLIKE and DISLIKE→NEUTRAL must not touch the like counts.
      case "NEUTRAL", "DISLIKE" -> {
        if (!e.wasLiked()) {
          LOGGER.debug(
              "Vote event did not remove a like, no counter change [assetId={}, action={}]",
              e.assetId(),
              e.action());
          yield Future.succeededFuture();
        }
        yield dao.decrementAssetLike(e)
            .compose(v -> dao.decrementProviderLike(e))
            .compose(v -> dao.decrementOrganizationLike(e));
      }

      case "DELETE" -> dao.deleteAssetAndAdjustLeaderboards(e);

      default -> Future.succeededFuture();
    };
  }

  private boolean isEligible(LeaderboardEvent e) {
    String action = e.action() == null ? "" : e.action().toUpperCase();
    // Only entry into the leaderboard (CREATE/UPDATE) needs the status check. Engagement
    // events (VIEW/DOWNLOAD/LIKE/...) imply the asset is already visible on the platform,
    // and DELETE must always run to keep leaderboards accurate.
    if (!action.equals("CREATE") && !action.equals("UPDATE")) {
      return true;
    }
    if (!"ACTIVE".equalsIgnoreCase(e.publishStatus())) {
      LOGGER.info(
          "Event not eligible: publishStatus is '{}' (expected ACTIVE) [assetId={}]",
          e.publishStatus(),
          e.assetId());
      return false;
    }
    if (e.assetType() != null && e.assetType().equalsIgnoreCase("USECASE")) {
      // For use cases, we only check publish status as data upload is not relevant
      return true;
    }
    if (!e.dataUploadStatus()) {
      LOGGER.info(
          "Event not eligible: dataUploadStatus is false (data not uploaded yet) [assetId={}]",
          e.assetId());
      return false;
    }
    return true;
  }
}
