package org.cdpg.dx.aaa.leaderboard.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.util.UUID;

import org.cdpg.dx.aaa.leaderboard.dao.ProviderLeaderboardDao;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ProviderLeaderboardDaoImpl implements ProviderLeaderboardDao {

  private final PostgresService pg;

  public ProviderLeaderboardDaoImpl(PostgresService pg) {
    this.pg = pg;
  }

  @Override
  public Future<Void> ensureExists(LeaderboardEvent e) {

    String sql =
        """
                INSERT INTO provider_leaderboard (
                  provider_id, provider_name,
                  organization_id, organization_name, organization_type
                )
                VALUES ($1,$2,$3,$4,$5)
                ON CONFLICT (provider_id) DO NOTHING
                """;

    JsonArray params =
        new JsonArray()
            .add(e.providerId())
            .add(null) // name updated via metadata events
            .add(e.organizationId())
            .add(e.organizationName())
            .add(e.organizationType());

    return pg.executeQuery(sql, params).mapEmpty();
  }

  @Override
  public Future<Void> incrementPublished(LeaderboardEvent e) {

    String sql =
        """
                UPDATE provider_leaderboard
                SET
                  total_published = total_published + 1,
                  published_databank = published_databank + CASE WHEN $2 = 'DATABANK' THEN 1 ELSE 0 END,
                  published_ai_models = published_ai_models + CASE WHEN $2 = 'AI_MODEL' THEN 1 ELSE 0 END,
                  published_usecases = published_usecases + CASE WHEN $2 = 'USECASE' THEN 1 ELSE 0 END,
                  updated_at = now()
                WHERE provider_id = $1
                """;

    return pg.executeQuery(sql, new JsonArray().add(e.providerId()).add(e.assetType())).mapEmpty();
  }

  @Override
  public Future<Void> decrementPublished(LeaderboardEvent e) {

    String sql =
        """
                UPDATE provider_leaderboard
                SET
                  total_published = GREATEST(total_published - 1, 0),
                  published_databank = GREATEST(published_databank - CASE WHEN $2 = 'DATABANK' THEN 1 ELSE 0 END, 0),
                  published_ai_models = GREATEST(published_ai_models - CASE WHEN $2 = 'AI_MODEL' THEN 1 ELSE 0 END, 0),
                  published_usecases = GREATEST(published_usecases - CASE WHEN $2 = 'USECASE' THEN 1 ELSE 0 END, 0),
                  updated_at = now()
                WHERE provider_id = $1
                """;

    return pg.executeQuery(sql, new JsonArray().add(e.providerId()).add(e.assetType())).mapEmpty();
  }

  @Override
  public Future<Void> incrementViews(UUID providerId) {
    return inc("views", providerId);
  }

  @Override
  public Future<Void> incrementDownloads(UUID providerId) {
    return inc("downloads", providerId);
  }

  @Override
  public Future<Void> incrementLikes(UUID providerId) {
    return inc("likes", providerId);
  }

  private Future<Void> inc(String column, UUID providerId) {
    String sql =
        "UPDATE provider_leaderboard SET "
            + column
            + " = "
            + column
            + " + 1, updated_at = now() "
            + "WHERE provider_id = $1";
    return pg.executeQuery(sql, new JsonArray().add(providerId)).mapEmpty();
  }
}
