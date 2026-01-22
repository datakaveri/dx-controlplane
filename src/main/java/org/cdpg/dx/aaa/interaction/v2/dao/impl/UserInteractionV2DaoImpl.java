package org.cdpg.dx.aaa.interaction.v2.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.interaction.v2.dao.UserInteractionV2Dao;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionDelta;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionRow;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class UserInteractionV2DaoImpl extends AbstractBaseDAO<InteractionRow>
    implements UserInteractionV2Dao {
  private static final Logger LOGGER = LogManager.getLogger(UserInteractionV2DaoImpl.class);

  private final PostgresService postgresService;

  public UserInteractionV2DaoImpl(PostgresService postgresService) {
    super(postgresService, "user_interactions", "id", InteractionRow::fromJson);
    this.postgresService = postgresService;
  }

  @Override
  public Future<PaginatedResult<InteractionRow>> getUserInteractions(
      PaginatedRequest paginatedRequest) {
    return getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<InteractionDelta> upsertInteractionWithDelta(
      UUID userId, UUID entityId, String entityType, String action) {

    String sql =
        """
    WITH existing AS (
      SELECT is_liked, is_disliked
      FROM user_interactions
      WHERE user_id = $1 AND entity_id = $2
    ),
    upsert AS (
      INSERT INTO user_interactions (
        user_id,
        entity_id,
        entity_type,
        is_liked,
        is_disliked,
        is_bookmarked
      )
      VALUES (
        $1, $2, $3,
        CASE WHEN $4 = 'LIKE' THEN TRUE ELSE FALSE END,
        CASE WHEN $4 = 'DISLIKE' THEN TRUE ELSE FALSE END,
        CASE WHEN $4 = 'BOOKMARK' THEN TRUE ELSE FALSE END
      )
      ON CONFLICT (user_id, entity_id)
      DO UPDATE SET
        is_liked = CASE
          WHEN $4 = 'LIKE' THEN TRUE
          WHEN $4 IN ('DISLIKE', 'NEUTRAL') THEN FALSE
          ELSE user_interactions.is_liked
        END,
        is_disliked = CASE
          WHEN $4 = 'DISLIKE' THEN TRUE
          WHEN $4 IN ('LIKE', 'NEUTRAL') THEN FALSE
          ELSE user_interactions.is_disliked
        END,
        is_bookmarked = CASE
          WHEN $4 = 'BOOKMARK' THEN TRUE
          WHEN $4 = 'UNBOOKMARK' THEN FALSE
          ELSE user_interactions.is_bookmarked
        END
      RETURNING is_liked, is_disliked
    )
    SELECT
      COALESCE(existing.is_liked, FALSE)    AS old_liked,
      COALESCE(existing.is_disliked, FALSE) AS old_disliked,
      upsert.is_liked                       AS new_liked,
      upsert.is_disliked                    AS new_disliked
    FROM upsert
    LEFT JOIN existing ON TRUE;
    """;

    JsonArray params =
        new JsonArray().add(userId.toString()).add(entityId.toString()).add(entityType).add(action);

    return postgresService
        .executeQuery(sql, params)
        .map(
            rows -> {
              if (rows.getRows().isEmpty()) {
                throw new IllegalStateException("No rows returned from upsertInteractionWithDelta");
              }

              JsonObject r = rows.getRows().getJsonObject(0);

              return new InteractionDelta(
                  r.getBoolean("old_liked", false),
                  r.getBoolean("old_disliked", false),
                  r.getBoolean("new_liked", false),
                  r.getBoolean("new_disliked", false));
            });
  }
}
