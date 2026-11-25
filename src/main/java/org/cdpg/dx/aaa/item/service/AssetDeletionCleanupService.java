package org.cdpg.dx.aaa.item.service;

import static org.cdpg.dx.aaa.asset.util.Constants.ASSET_REQUEST_TABLE;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.DeleteQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

/**
 * Removes Control Plane rows that reference an asset once that asset has been deleted from the
 * catalogue, so they do not linger as stale entries (e.g. N/A rows on the Asset File Upload page).
 *
 * <p>Runs asynchronously after the delete response has been sent: a cleanup failure never fails
 * or delays the item deletion. Each table is cleaned independently and retried with backoff.
 *
 * <p>To clean another table, add it to {@link #DEPENDENT_TABLES}. Only add tables holding live
 * state; audit/history tables (user_activity_log, provider_feedback, ...) are kept on purpose.
 */
public class AssetDeletionCleanupService {
  private static final Logger LOGGER = LogManager.getLogger(AssetDeletionCleanupService.class);

  /** A table and the column in it that holds the asset id. */
  public record AssetReference(String table, String assetIdColumn) {}

  static final List<AssetReference> DEPENDENT_TABLES =
      List.of(new AssetReference(ASSET_REQUEST_TABLE, "asset_id"));

  static final int MAX_ATTEMPTS = 3;
  static final long INITIAL_BACKOFF_MS = 2_000;

  private final PostgresService postgresService;
  private final List<AssetReference> dependentTables;

  public AssetDeletionCleanupService(PostgresService postgresService) {
    this(postgresService, DEPENDENT_TABLES);
  }

  AssetDeletionCleanupService(
      PostgresService postgresService, List<AssetReference> dependentTables) {
    this.postgresService = postgresService;
    this.dependentTables = dependentTables;
  }

  /**
   * Fire-and-forget cleanup of every dependent table for {@code assetId}. The returned future
   * completes once all tables have been handled (successfully or after exhausting retries); it
   * never fails, so callers may ignore it.
   */
  public Future<Void> cleanupAsync(Vertx vertx, UUID assetId) {
    List<Future<Void>> tasks =
        dependentTables.stream().map(ref -> cleanupWithRetry(vertx, ref, assetId, 1)).toList();
    return Future.join(tasks).<Void>mapEmpty().otherwiseEmpty();
  }

  private Future<Void> cleanupWithRetry(
      Vertx vertx, AssetReference ref, UUID assetId, int attempt) {
    return deleteReferences(ref, assetId)
        .recover(
            err -> {
              if (attempt >= MAX_ATTEMPTS) {
                LOGGER.error(
                    "Cleanup of {} for deleted asset {} failed after {} attempts; manual cleanup"
                        + " needed: {}",
                    ref.table(),
                    assetId,
                    attempt,
                    err.getMessage(),
                    err);
                return Future.succeededFuture();
              }
              long delay = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
              LOGGER.warn(
                  "Cleanup of {} for deleted asset {} failed (attempt {}/{}), retrying in {} ms:"
                      + " {}",
                  ref.table(),
                  assetId,
                  attempt,
                  MAX_ATTEMPTS,
                  delay,
                  err.getMessage());
              Promise<Void> retry = Promise.promise();
              vertx.setTimer(
                  delay,
                  id -> cleanupWithRetry(vertx, ref, assetId, attempt + 1).onComplete(retry));
              return retry.future();
            });
  }

  private Future<Void> deleteReferences(AssetReference ref, UUID assetId) {
    Condition condition =
        new Condition(ref.assetIdColumn(), Condition.Operator.EQUALS, List.of(assetId.toString()));
    return postgresService
        .delete(new DeleteQuery(ref.table(), condition, null, null))
        .onSuccess(
            res ->
                LOGGER.info(
                    "Cleaned up {} for deleted asset {} (rows affected: {})",
                    ref.table(),
                    assetId,
                    res.isRowsAffected()))
        .mapEmpty();
  }
}
