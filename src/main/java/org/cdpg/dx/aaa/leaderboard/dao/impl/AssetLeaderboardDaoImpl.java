package org.cdpg.dx.aaa.leaderboard.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.util.UUID;

import org.cdpg.dx.aaa.leaderboard.dao.AssetLeaderboardDao;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AssetLeaderboardDaoImpl implements AssetLeaderboardDao {

  private final PostgresService pg;

  public AssetLeaderboardDaoImpl(PostgresService pg) {
    this.pg = pg;
  }

  @Override
  public Future<Void> upsert(LeaderboardEvent e) {

    String sql =
        """
                INSERT INTO asset_leaderboard (
                  asset_id, asset_name, asset_type, access_policy,
                  provider_id,
                  organization_id, organization_name, organization_type
                )
                VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
                ON CONFLICT (asset_id)
                DO UPDATE SET
                  asset_name = EXCLUDED.asset_name,
                  access_policy = EXCLUDED.access_policy,
                  organization_name = EXCLUDED.organization_name,
                  organization_type = EXCLUDED.organization_type,
                  updated_at = now()
                """;

    JsonArray params =
        new JsonArray()
            .add(e.assetId())
            .add(e.assetName())
            .add(e.assetType())
            .add(e.accessPolicy())
            .add(e.providerId())
            .add(e.organizationId())
            .add(e.organizationName())
            .add(e.organizationType());

    return pg.executeQuery(sql, params).mapEmpty();
  }

  @Override
  public Future<Void> incrementViews(UUID assetId) {
    return inc("views", assetId);
  }

  @Override
  public Future<Void> incrementDownloads(UUID assetId) {
    return inc("downloads", assetId);
  }

  @Override
  public Future<Void> incrementLikes(UUID assetId) {
    return inc("likes", assetId);
  }

  private Future<Void> inc(String column, UUID assetId) {
    String sql =
        "UPDATE asset_leaderboard SET "
            + column
            + " = "
            + column
            + " + 1, updated_at = now() "
            + "WHERE asset_id = $1";
    return pg.executeQuery(sql, new JsonArray().add(assetId)).mapEmpty();
  }

  @Override
  public Future<Void> delete(UUID assetId) {
    String sql = "DELETE FROM asset_leaderboard WHERE asset_id = $1";
    return pg.executeQuery(sql, new JsonArray().add(assetId)).mapEmpty();
  }
}
