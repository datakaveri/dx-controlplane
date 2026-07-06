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
    LOGGER.info(
        "Processing event: action={}, assetId={}, providerId={}, organizationId={}, publishStatus={}, dataUploadStatus={}",
        e.action(),
        e.assetId(),
        e.providerId(),
        e.organizationId(),
        e.publishStatus(),
        e.dataUploadStatus());
    LOGGER.info("is eligible: {}", isEligible(e));
    if (!isEligible(e)) {
      LOGGER.info("Event is not eligible for processing, skipping leaderboard updates");
      return Future.succeededFuture();
    }
    LOGGER.info("Event is eligible for processing, proceeding with leaderboard updates");
    return switch (e.action().toUpperCase()) {
      case "CREATE", "UPDATE" ->
          dao.upsertAssetOnCreate(e)
              .compose(
                  newAsset ->
                      dao.upsertProviderOnCreate(e, newAsset)
                          .compose(v -> dao.upsertOrganizationOnCreate(e, newAsset)));

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
      case "NEUTRAL", "DISLIKE" ->
          dao.decrementAssetLike(e)
              .compose(v -> dao.decrementProviderLike(e))
              .compose(v -> dao.decrementOrganizationLike(e));

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
    if (e.assetType() != null && e.assetType().equalsIgnoreCase("USECASE")) {
      // For use cases, we only check publish status as data upload is not relevant
      return "ACTIVE".equalsIgnoreCase(e.publishStatus());
    }

    return e.dataUploadStatus() && "ACTIVE".equalsIgnoreCase(e.publishStatus());
  }
}
