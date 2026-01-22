package org.cdpg.dx.aaa.interaction.dao.impl;

import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.interaction.dao.UserInteractionDao;
import org.cdpg.dx.aaa.interaction.model.InteractionRow;
import org.cdpg.dx.aaa.interaction.model.UserInteraction;
import org.cdpg.dx.aaa.interaction.model.UserInteractionsPaginatedResponse;
import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.DeleteQuery;
import org.cdpg.dx.database.postgres.models.UpsertResult;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class UserInteractionDaoImpl extends AbstractBaseDAO<UserInteraction>
    implements UserInteractionDao {
  private static final Logger LOGGER = LogManager.getLogger(UserInteractionDaoImpl.class);

  public UserInteractionDaoImpl(PostgresService postgresService) {
    super(postgresService, "user_interactions", "id", UserInteraction::fromJson);
  }

  @Override
  public Future<UpsertResult<UserInteraction>> upsert(UserInteraction interaction) {
    return super.upsertNew(
        interaction, List.of("user_id", "entity_id", "action_type"), List.of("value"));
  }

  @Override
  public Future<Void> delete(UUID userId, UUID entityId, String actionType) {
    Condition condition =
        new Condition(
            List.of(
                new Condition("user_id", Condition.Operator.EQUALS, List.of(userId.toString())),
                new Condition("entity_id", Condition.Operator.EQUALS, List.of(entityId.toString())),
                new Condition("action_type", Condition.Operator.EQUALS, List.of(actionType))),
            Condition.LogicalOperator.AND);

    DeleteQuery query = new DeleteQuery("user_interactions", condition, null, null);
    return postgresService.delete(query).mapEmpty();
  }

  @Override
  public Future<UserInteractionsPaginatedResponse> fetchUserInteractions(PaginatedRequest request) {

    int page = request.page();
    int size = request.size();
    int offset = (page - 1) * size;

    Map<String, Object> filters = request.filters();
    LOGGER.debug("filters: {}", filters);

    String userId = (String) filters.get("user_id"); // mandatory

    // -----------------------------
    // Build dynamic WHERE clause
    // -----------------------------
    StringBuilder where = new StringBuilder(" WHERE user_id = $1 ");
    JsonArray params = new JsonArray();
    params.add(userId);

    AtomicInteger index = new AtomicInteger(2);

    // helper: normalize everything to list and always use IN
    java.util.function.BiConsumer<String, Object> applyFilter =
        (column, rawValue) -> {
          if (rawValue == null) return;

          List<?> values;

          if (rawValue instanceof List<?> list) {
            if (list.isEmpty()) return;
            values = list;
          } else {
            values = List.of(rawValue);
          }

          where.append(" AND ").append(column).append(" IN (");

          for (int i = 0; i < values.size(); i++) {
            if (i > 0) where.append(", ");
            where.append("$").append(index.getAndIncrement());
            params.add(values.get(i));
          }

          where.append(")");
        };

    // Apply optional filters
    applyFilter.accept("entity_type", filters.get("entity_type"));
    applyFilter.accept("action_type", filters.get("action_type"));
    applyFilter.accept("entity_id", filters.get("entity_id"));
    applyFilter.accept("value", filters.get("value"));

    // -----------------------------
    // Final SQL
    // -----------------------------
    String sql =
        """
            SELECT
                entity_id,
                MAX(entity_type) AS entity_type,

                BOOL_OR(action_type = 'BOOKMARK') AS is_bookmarked,
                BOOL_OR(action_type = 'VOTE' AND value = 'LIKE') AS is_liked,
                BOOL_OR(action_type = 'VOTE' AND value = 'DISLIKE') AS is_disliked,

                COUNT(*) OVER() AS total_count
            FROM user_interactions
            """
            + where
            + """
      GROUP BY entity_id
      ORDER BY entity_id
      LIMIT $"""
            + index
            + " OFFSET $"
            + (index.get() + 1);

    params.add(size);
    params.add(offset);

    LOGGER.debug("SQL: {}", sql);
    LOGGER.debug("Params: {}", params);

    // -----------------------------
    // Execute query
    // -----------------------------
    return postgresService
        .executeQuery(sql, params)
        .map(
            rows -> {
              List<InteractionRow> result =
                  rows.getRows().stream()
                      .map(
                          obj -> {
                            JsonObject r = (JsonObject) obj;

                            return new InteractionRow(
                                r.getString("entity_id"),
                                EntityType.valueOf(r.getString("entity_type")),
                                r.getBoolean("is_bookmarked"),
                                r.getBoolean("is_liked"),
                                r.getBoolean("is_disliked"));
                          })
                      .toList();

              long total = rows.getTotalCount();

              return new UserInteractionsPaginatedResponse(
                  result, PaginationInfo.from(page, size, total));
            });
  }
}
