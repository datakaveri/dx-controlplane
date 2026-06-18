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
              .compose(v -> dao.upsertProviderOnCreate(e))
              .compose(v -> dao.upsertOrganizationOnCreate(e));

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
    if (e.action() != null && e.action().equalsIgnoreCase("DELETE")) {
      // For deletes, we want to process regardless of publish or data upload status to ensure
      // leaderboards are accurate
      return true;
    }
    if (e.assetType() != null && e.assetType().equalsIgnoreCase("USECASE")) {
      // For use cases, we only check publish status as data upload is not relevant
      return "ACTIVE".equalsIgnoreCase(e.publishStatus());
    }

    return e.dataUploadStatus() && "ACTIVE".equalsIgnoreCase(e.publishStatus());
  }
}
