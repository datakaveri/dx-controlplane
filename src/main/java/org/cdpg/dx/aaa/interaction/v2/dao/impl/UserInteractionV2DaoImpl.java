package org.cdpg.dx.aaa.interaction.v2.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;
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
  public Future<PaginatedResult<InteractionRow>> getUserInteractions(PaginatedRequest request) {

    Map<String, Object> filters = request.filters();

    Object actionObj = filters.remove("action_type");

    if (actionObj != null) {

      List<?> actions;

      if (actionObj instanceof List<?> list) {
        actions = list;
      } else {
        actions = List.of(actionObj);
      }

      for (Object a : actions) {
        String action = a.toString().toUpperCase();

        switch (action) {
          case "LIKE" -> filters.put("is_liked", true);
          case "DISLIKE" -> filters.put("is_disliked", true);
          case "BOOKMARK" -> filters.put("is_bookmarked", true);
          default -> LOGGER.warn("Unknown action type filter: {}", action);
        }
      }
    }

    return getAllWithFilters(request);
  }

  @Override
  public Future<InteractionDelta> upsertInteractionWithDelta(
      UUID userId, UUID entityId, String entityType, String action) {

    String sql =
        """
    WITH existing AS (
      SELECT is_liked, is_disliked, is_bookmarked
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
      RETURNING is_liked, is_disliked, is_bookmarked
    )
    SELECT
      $2                                     AS entity_id,
      $3                                     AS entity_type,

      COALESCE(existing.is_liked, FALSE)       AS old_liked,
      COALESCE(existing.is_disliked, FALSE)    AS old_disliked,
      COALESCE(existing.is_bookmarked, FALSE)  AS old_bookmarked,

      upsert.is_liked                          AS new_liked,
      upsert.is_disliked                       AS new_disliked,
      upsert.is_bookmarked                     AS new_bookmarked
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
                  r.getString("entity_id"),
                  r.getString("entity_type"),
                  r.getBoolean("old_liked", false),
                  r.getBoolean("old_disliked", false),
                  r.getBoolean("new_liked", false),
                  r.getBoolean("new_disliked", false),
                  r.getBoolean("old_bookmarked", false),
                  r.getBoolean("new_bookmarked", false));
            });
  }
}
