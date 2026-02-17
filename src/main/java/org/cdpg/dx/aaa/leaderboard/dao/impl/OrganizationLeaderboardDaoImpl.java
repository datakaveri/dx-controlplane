package org.cdpg.dx.aaa.leaderboard.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.util.UUID;

import org.cdpg.dx.aaa.leaderboard.dao.OrganizationLeaderboardDao;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class OrganizationLeaderboardDaoImpl implements OrganizationLeaderboardDao {

  private final PostgresService pg;

  public OrganizationLeaderboardDaoImpl(PostgresService pg) {
    this.pg = pg;
  }

  @Override
  public Future<Void> ensureExists(LeaderboardEvent e) {

    String sql =
        """
                INSERT INTO organization_leaderboard (
                  organization_id, organization_name, organization_type
                )
                VALUES ($1,$2,$3)
                ON CONFLICT (organization_id) DO NOTHING
                """;

    return pg.executeQuery(
            sql,
            new JsonArray()
                .add(e.organizationId())
                .add(e.organizationName())
                .add(e.organizationType()))
        .mapEmpty();
  }

  @Override
  public Future<Void> incrementPublished(LeaderboardEvent e) {

    String sql =
        """
                UPDATE organization_leaderboard
                SET
                  total_published = total_published + 1,
                  published_databank = published_databank + CASE WHEN $2 = 'DATABANK' THEN 1 ELSE 0 END,
                  published_ai_models = published_ai_models + CASE WHEN $2 = 'AI_MODEL' THEN 1 ELSE 0 END,
                  published_usecases = published_usecases + CASE WHEN $2 = 'USECASE' THEN 1 ELSE 0 END,
                  updated_at = now()
                WHERE organization_id = $1
                """;

    return pg.executeQuery(sql, new JsonArray().add(e.organizationId()).add(e.assetType()))
        .mapEmpty();
  }

  @Override
  public Future<Void> decrementPublished(LeaderboardEvent e) {

    String sql =
        """
                UPDATE organization_leaderboard
                SET
                  total_published = GREATEST(total_published - 1, 0),
                  published_databank = GREATEST(published_databank - CASE WHEN $2 = 'DATABANK' THEN 1 ELSE 0 END, 0),
                  published_ai_models = GREATEST(published_ai_models - CASE WHEN $2 = 'AI_MODEL' THEN 1 ELSE 0 END, 0),
                  published_usecases = GREATEST(published_usecases - CASE WHEN $2 = 'USECASE' THEN 1 ELSE 0 END, 0),
                  updated_at = now()
                WHERE organization_id = $1
                """;

    return pg.executeQuery(sql, new JsonArray().add(e.organizationId()).add(e.assetType()))
        .mapEmpty();
  }

  @Override
  public Future<Void> incrementViews(UUID orgId) {
    return inc("views", orgId);
  }

  @Override
  public Future<Void> incrementDownloads(UUID orgId) {
    return inc("downloads", orgId);
  }

  @Override
  public Future<Void> incrementLikes(UUID orgId) {
    return inc("likes", orgId);
  }

  private Future<Void> inc(String column, UUID orgId) {
    String sql =
        "UPDATE organization_leaderboard SET "
            + column
            + " = "
            + column
            + " + 1, updated_at = now() "
            + "WHERE organization_id = $1";
    return pg.executeQuery(sql, new JsonArray().add(orgId)).mapEmpty();
  }
}
