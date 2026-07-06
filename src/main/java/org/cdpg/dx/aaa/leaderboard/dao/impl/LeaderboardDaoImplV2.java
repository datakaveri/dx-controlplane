package org.cdpg.dx.aaa.leaderboard.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.leaderboard.dao.LeaderboardDaoV2;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.UUID;

public class LeaderboardDaoImplV2 implements LeaderboardDaoV2 {

  private static final Logger LOGGER = LogManager.getLogger(LeaderboardDaoImplV2.class);
  private final PostgresService postgresService;

  public LeaderboardDaoImplV2(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

  // ------------------------------------------------
  // ASSET
  // ------------------------------------------------

  /**
   * Upserts the asset row and reports whether it was newly inserted, so callers can bump
   * provider/organization published counters exactly once per asset (UPDATE events must not
   * re-count). Organization fields are nullable — platform providers publish without an org.
   */
  public Future<Boolean> upsertAssetOnCreate(LeaderboardEvent e) {

    String sql =
        """
            INSERT INTO asset_leaderboard (
              asset_id, asset_name, asset_type, access_policy, provider_id, provider_name,
              organization_id, organization_name, organization_type, data_upload_status, publish_status
            )
            VALUES ($1, $2, $3, $4, $5,
              COALESCE($6, (SELECT ou.user_name
                            FROM aaa.organization_users ou
                            WHERE ou.user_id = $5
                            LIMIT 1)),
              $7, $8, $9, $10, $11
            )
            ON CONFLICT (asset_id)
            DO UPDATE SET
              asset_name        = EXCLUDED.asset_name,
              asset_type        = EXCLUDED.asset_type,
              access_policy     = EXCLUDED.access_policy,
              provider_id       = EXCLUDED.provider_id,
              provider_name     = COALESCE(EXCLUDED.provider_name, asset_leaderboard.provider_name),
              organization_id   = COALESCE(EXCLUDED.organization_id, asset_leaderboard.organization_id),
              organization_name = COALESCE(EXCLUDED.organization_name, asset_leaderboard.organization_name),
              organization_type = COALESCE(EXCLUDED.organization_type, asset_leaderboard.organization_type),
              data_upload_status = EXCLUDED.data_upload_status,
              publish_status = EXCLUDED.publish_status
            RETURNING (xmax = 0) AS inserted
          """;
    LOGGER.debug("Upserting asset leaderboard : {}", e.toString());
    JsonArray params =
        new JsonArray()
            .add(e.assetId().toString())
            .add(e.assetName())
            .add(e.assetType())
            .add(e.accessPolicy())
            .add(e.providerId().toString()) // $5 used twice
            .add(e.providerName())
            .add(toStringOrNull(e.organizationId()))
            .add(e.organizationName())
            .add(e.organizationType())
            .add(e.dataUploadStatus())
            .add(e.publishStatus());
    LOGGER.debug("Executing SQL: {}, with params: {}", sql, params.encode());
    return postgresService
        .executeQuery(sql, params)
        .map(
            result ->
                !result.getRows().isEmpty()
                    && Boolean.TRUE.equals(
                        result.getRows().getJsonObject(0).getBoolean("inserted")));
  }

  @Override
  public Future<Void> incrementAssetView(LeaderboardEvent e) {
    LOGGER.debug("Incrementing asset view for asset_id={}", e.assetId());
    return incrementCounter("asset_leaderboard", "views", "asset_id", e.assetId());
  }

  @Override
  public Future<Void> incrementAssetDownload(LeaderboardEvent e) {
    return incrementCounter("asset_leaderboard", "downloads", "asset_id", e.assetId());
  }

  @Override
  public Future<Void> incrementAssetLike(LeaderboardEvent e) {
    return incrementCounter("asset_leaderboard", "likes", "asset_id", e.assetId());
  }

  // ------------------------------------------------
  // PROVIDER
  // ------------------------------------------------
  @Override
  public Future<Void> upsertProviderOnCreate(LeaderboardEvent e, boolean newAsset) {

    String sql =
        """
            INSERT INTO aaa.provider_leaderboard (
              provider_id,
              provider_name,
              organization_id,
              organization_name,
              organization_type,
              published_databank,
              published_ai_models,
              published_usecases,
              total_published,
              downloads,
              likes,
              views,
              updated_at
            )
            VALUES (
              $1,
              COALESCE($2, (SELECT ou.user_name
                            FROM aaa.organization_users ou
                            WHERE ou.user_id = $1
                            LIMIT 1)),
              $3, $4, $5,
              $6, $7, $8,
              $9,
              0, 0, 0,
              now()
            )
            ON CONFLICT (provider_id)
            DO UPDATE SET
              provider_name        = COALESCE(EXCLUDED.provider_name, provider_leaderboard.provider_name),
              organization_id      = COALESCE(EXCLUDED.organization_id, provider_leaderboard.organization_id),
              organization_name    = COALESCE(EXCLUDED.organization_name, provider_leaderboard.organization_name),
              organization_type    = COALESCE(EXCLUDED.organization_type, provider_leaderboard.organization_type),
              published_databank   = provider_leaderboard.published_databank + EXCLUDED.published_databank,
              published_ai_models  = provider_leaderboard.published_ai_models + EXCLUDED.published_ai_models,
              published_usecases   = provider_leaderboard.published_usecases + EXCLUDED.published_usecases,
              total_published      = provider_leaderboard.total_published + EXCLUDED.total_published,
              updated_at           = now();
            """;

    // UPDATE events on an existing asset must still refresh names but not re-count publishes
    int db = newAsset && e.assetType().equals("DATABANK") ? 1 : 0;
    int ai = newAsset && e.assetType().equals("AI_MODEL") ? 1 : 0;
    int uc = newAsset && e.assetType().equals("USECASE") ? 1 : 0;
    int total = newAsset ? 1 : 0;

    JsonArray params =
        new JsonArray()
            .add(e.providerId().toString()) // UUID is OK directly
            .add(e.providerName())
            .add(toStringOrNull(e.organizationId()))
            .add(e.organizationName())
            .add(e.organizationType())
            .add(db)
            .add(ai)
            .add(uc)
            .add(total);

    LOGGER.debug("Upserting provider leaderboard : {}", e);
    LOGGER.debug("Executing SQL with params: {}", params.encode());

    return postgresService.executeQuery(sql, params).mapEmpty();
  }

  @Override
  public Future<Void> incrementProviderView(LeaderboardEvent e) {
    LOGGER.debug("Incrementing provider view for provider_id={}", e.providerId());
    return incrementCounter("provider_leaderboard", "views", "provider_id", e.providerId());
  }

  @Override
  public Future<Void> incrementProviderDownload(LeaderboardEvent e) {
    return incrementCounter("provider_leaderboard", "downloads", "provider_id", e.providerId());
  }

  @Override
  public Future<Void> incrementProviderLike(LeaderboardEvent e) {
    return incrementCounter("provider_leaderboard", "likes", "provider_id", e.providerId());
  }

  // ------------------------------------------------
  // ORGANIZATION
  // ------------------------------------------------

  @Override
  public Future<Void> upsertOrganizationOnCreate(LeaderboardEvent e, boolean newAsset) {

    // Platform providers publish directly on the exchange without an organisation
    if (e.organizationId() == null) {
      LOGGER.debug(
          "No organization on event [assetId={}], skipping organization leaderboard", e.assetId());
      return Future.succeededFuture();
    }

    String sql =
        """
            INSERT INTO aaa.organization_leaderboard (
              organization_id,
              organization_name,
              organization_type,
              published_databank,
              published_ai_models,
              published_usecases,
              total_published,
              downloads,
              likes,
              views,
              updated_at
            )
            VALUES (
              $1, $2, $3,
              $4, $5, $6,
              $7,
              0, 0, 0,
              now()
            )
            ON CONFLICT (organization_id)
            DO UPDATE SET
              organization_name    = COALESCE(EXCLUDED.organization_name, organization_leaderboard.organization_name),
              organization_type    = COALESCE(EXCLUDED.organization_type, organization_leaderboard.organization_type),
              published_databank   = organization_leaderboard.published_databank + EXCLUDED.published_databank,
              published_ai_models  = organization_leaderboard.published_ai_models + EXCLUDED.published_ai_models,
              published_usecases   = organization_leaderboard.published_usecases + EXCLUDED.published_usecases,
              total_published      = organization_leaderboard.total_published + EXCLUDED.total_published,
              updated_at           = now();
            """;

    // UPDATE events on an existing asset must still refresh names but not re-count publishes
    int db = newAsset && e.assetType().equals("DATABANK") ? 1 : 0;
    int ai = newAsset && e.assetType().equals("AI_MODEL") ? 1 : 0;
    int uc = newAsset && e.assetType().equals("USECASE") ? 1 : 0;
    int total = newAsset ? 1 : 0;

    JsonArray params =
        new JsonArray()
            .add(e.organizationId().toString())
            .add(e.organizationName())
            .add(e.organizationType())
            .add(db)
            .add(ai)
            .add(uc)
            .add(total);

    LOGGER.debug("Upserting organization leaderboard : {}", e);
    LOGGER.debug("Executing SQL with params: {}", params.encode());

    return postgresService.executeQuery(sql, params).mapEmpty();
  }

  @Override
  public Future<Void> incrementOrganizationView(LeaderboardEvent e) {
    LOGGER.debug("Incrementing organization view for organization_id={}", e.organizationId());
    return incrementCounter(
        "organization_leaderboard", "views", "organization_id", e.organizationId());
  }

  @Override
  public Future<Void> incrementOrganizationDownload(LeaderboardEvent e) {
    return incrementCounter(
        "organization_leaderboard", "downloads", "organization_id", e.organizationId());
  }

  @Override
  public Future<Void> incrementOrganizationLike(LeaderboardEvent e) {
    return incrementCounter(
        "organization_leaderboard", "likes", "organization_id", e.organizationId());
  }

  @Override
  public Future<Void> decrementAssetLike(LeaderboardEvent e) {
    return decrementCounter("asset_leaderboard", "likes", "asset_id", e.assetId());
  }

  @Override
  public Future<Void> decrementProviderLike(LeaderboardEvent e) {
    return decrementCounter("provider_leaderboard", "likes", "provider_id", e.providerId());
  }

  @Override
  public Future<Void> decrementOrganizationLike(LeaderboardEvent e) {
    return decrementCounter(
        "organization_leaderboard", "likes", "organization_id", e.organizationId());
  }

  // ------------------------------------------------
  // DELETE
  // ------------------------------------------------

  @Override
  public Future<Void> deleteAsset(LeaderboardEvent e) {
    String sql = "DELETE FROM asset_leaderboard WHERE asset_id = $1";
    return postgresService
        .executeQuery(sql, new JsonArray().add(e.assetId().toString()))
        .mapEmpty();
  }

  private static String toStringOrNull(UUID id) {
    return id != null ? id.toString() : null;
  }

  // ------------------------------------------------
  // COMMON COUNTER (UPDATE ONLY)
  // ------------------------------------------------

  private Future<Void> incrementCounter(String table, String column, String idColumn, UUID id) {
    if (id == null) {
      LOGGER.debug("No {} on event, skipping {} increment on {}", idColumn, column, table);
      return Future.succeededFuture();
    }
    LOGGER.info("Incrementing {} in {} for id={}", column, table, id);

    String sql =
        String.format(
            """
                    UPDATE %s
                    SET
                      %s = %s + 1,
                      updated_at = now()
                    WHERE %s = $1
                    """,
            table, column, column, idColumn);

    LOGGER.debug("Incrementing {} in {} for id={}", column, table, id);
    return postgresService.executeQuery(sql, new JsonArray().add(id.toString())).mapEmpty();
  }

  private Future<Void> decrementCounter(String table, String column, String idColumn, UUID id) {
    if (id == null) {
      LOGGER.debug("No {} on event, skipping {} decrement on {}", idColumn, column, table);
      return Future.succeededFuture();
    }
    LOGGER.info("Decrementing {} in {} for id={}", column, table, id);

    String sql =
        String.format(
            """
                    UPDATE %s
                    SET
                      %s = GREATEST(%s - 1, 0),
                      updated_at = now()
                    WHERE %s = $1
                    """,
            table, column, column, idColumn);

    return postgresService.executeQuery(sql, new JsonArray().add(id.toString())).mapEmpty();
  }

  @Override
  public Future<Void> deleteAssetAndAdjustLeaderboards(LeaderboardEvent e) {

    // 1️⃣ Fetch asset metadata (NO engagement metrics needed)
    String fetchSql =
        """
            SELECT
              asset_type,
              provider_id,
              organization_id
            FROM aaa.asset_leaderboard
            WHERE asset_id = $1
            """;

    return postgresService
        .executeQuery(fetchSql, new JsonArray().add(e.assetId().toString()))
        .compose(
            rs -> {

              // Asset already deleted → idempotent
              if (rs.getRows().isEmpty()) {
                return Future.succeededFuture();
              }

              JsonObject row = rs.getRows().getJsonObject(0);

              String assetType = row.getString("asset_type");
              String providerId = row.getString("provider_id");
              String organizationId = row.getString("organization_id");

              int db = assetType.equals("DATABANK") ? 1 : 0;
              int ai = assetType.equals("AI_MODEL") ? 1 : 0;
              int uc = assetType.equals("USECASE") ? 1 : 0;

              // 2️⃣ Provider inventory decrement
              String providerUpdateSql =
                  """
                      UPDATE aaa.provider_leaderboard
                      SET
                        published_databank  = GREATEST(published_databank  - $1, 0),
                        published_ai_models = GREATEST(published_ai_models - $2, 0),
                        published_usecases  = GREATEST(published_usecases  - $3, 0),
                        total_published     = GREATEST(total_published - 1, 0),
                        updated_at          = now()
                      WHERE provider_id = $4
                      """;

              // 3️⃣ Organization inventory decrement
              String orgUpdateSql =
                  """
                      UPDATE aaa.organization_leaderboard
                      SET
                        published_databank  = GREATEST(published_databank  - $1, 0),
                        published_ai_models = GREATEST(published_ai_models - $2, 0),
                        published_usecases  = GREATEST(published_usecases  - $3, 0),
                        total_published     = GREATEST(total_published - 1, 0),
                        updated_at          = now()
                      WHERE organization_id = $4
                      """;

              JsonArray counterParams = new JsonArray().add(db).add(ai).add(uc);

              Future<?> providerAdjust =
                  providerId == null
                      ? Future.succeededFuture()
                      : postgresService.executeQuery(
                          providerUpdateSql, counterParams.copy().add(providerId));

              return providerAdjust
                  .compose(
                      v ->
                          organizationId == null
                              ? Future.succeededFuture()
                              : postgresService.executeQuery(
                                  orgUpdateSql, counterParams.copy().add(organizationId)))
                  .compose(
                      v ->
                          postgresService.executeQuery(
                              "DELETE FROM aaa.asset_leaderboard WHERE asset_id = $1",
                              new JsonArray().add(e.assetId().toString())))
                  .mapEmpty();
            });
  }
}
