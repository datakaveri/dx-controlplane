package org.cdpg.dx.aaa.interaction.v2.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.interaction.dao.impl.UserInteractionDaoImpl;
import org.cdpg.dx.aaa.interaction.v2.dao.UserFeedbackDao;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedbackPaginatedResponse;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class UserFeedbackDaoImpl extends AbstractBaseDAO<UserFeedback>
  implements UserFeedbackDao {

  private static final Logger LOGGER = LogManager.getLogger(UserInteractionDaoImpl.class);

  public UserFeedbackDaoImpl(PostgresService postgresService) {
    super(postgresService, "user_interactions", "id", UserFeedback::fromJson);
  }


  @Override
  public Future<UserFeedback> updateFeedback(UserFeedback userFeedback) {
    JsonObject json = userFeedback.toJson();

    String userId = json.getString("user_id");
    String assetId = json.getString("asset_id");

    String subtype = json.getString("action_subtype");
    JsonObject subdata = json.getJsonObject("action_subdata");

    boolean hasSubtype = json.containsKey("action_subtype") && json.getString("action_subtype") != null;
    boolean hasSubdata = json.containsKey("action_subdata") && json.getJsonObject("action_subdata") != null;

    if (hasSubtype && !hasSubdata) {
      return Future.failedFuture(
        new IllegalArgumentException("action_subdata is required when action_subtype is provided"));
    }

    if (!hasSubtype && hasSubdata) {
      return Future.failedFuture(
        new IllegalArgumentException("action_subtype is required when action_subdata is provided"));
    }

    Integer rating = json.getInteger("entity_rating");

    if (rating != null && (rating < 1 || rating > 5)) {
      return Future.failedFuture(
        new IllegalArgumentException(
          "entity_rating must be between 1 and 10"
        )
      );
    }

    List<String> columns = new ArrayList<>();
    List<Object> values = new ArrayList<>();

    if (json.containsKey("action_subtype")) {
      columns.add("action_subtype");
      values.add(json.getString("action_subtype"));
    }

    if (json.containsKey("action_subdata")) {
      columns.add("action_subdata");
      values.add(json.getJsonObject("action_subdata"));
    }

    if (json.containsKey("entity_rating")) {
      columns.add("entity_rating");
      values.add(json.getInteger("entity_rating"));
    }

    if (columns.isEmpty()) {
      return Future.succeededFuture(userFeedback); // nothing to update
    }

    Condition condition =
      new Condition(
        List.of(
          new Condition("user_id", Condition.Operator.EQUALS, List.of(userId)),
          new Condition("asset_id", Condition.Operator.EQUALS, List.of(assetId))
        ),
        Condition.LogicalOperator.AND
      );

    UpdateQuery query =
      new UpdateQuery()
        .setTable("user_interactions")
        .setColumns(columns)
        .setValues(values)
        .setCondition(condition);

    return postgresService.update(query).mapEmpty();
  }

  @Override
  public Future<UserFeedbackPaginatedResponse> fetchUserFeedbacks(PaginatedRequest request) {

    int page = request.page();
    int size = request.size();
    int offset = (page - 1) * size;

    Map<String, Object> filters = request.filters();
    LOGGER.debug("filters: {}", filters);


    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    JsonArray params = new JsonArray();
    AtomicInteger index = new AtomicInteger(1);

    // helper to support both single value and list → IN clause
    java.util.function.BiConsumer<String, Object> applyFilter =
      (column, rawValue) -> {
        if (rawValue == null) return;

        List<?> values =
          rawValue instanceof List<?> list
            ? list
            : List.of(rawValue);

        if (values.isEmpty()) return;

        where.append(" AND ").append(column).append(" IN (");

        for (int i = 0; i < values.size(); i++) {
          if (i > 0) where.append(", ");
          where.append("$").append(index.getAndIncrement());
          params.add(values.get(i));
        }

        where.append(")");
      };

    // Apply filters
    applyFilter.accept("user_id", filters.get("user_id"));
    applyFilter.accept("asset_id", filters.get("asset_id"));
    applyFilter.accept("action_subtype", filters.get("action_subtype"));

    int limitIndex = index.getAndIncrement();
    int offsetIndex = index.getAndIncrement();

    // -----------------------------
    // Final SQL
    // -----------------------------
    String sql =
      """
      SELECT
          id,
          user_id,
          asset_id,
          entity_rating,
          action_subtype,
          action_subdata,
          COUNT(*) OVER() AS total_count
      FROM user_interactions
      """
        + where
        + """
      ORDER BY asset_id
      LIMIT $"""
        + limitIndex
        + " OFFSET $"
        + offsetIndex;

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

          List<UserFeedback> result =
            rows.getRows().stream()
              .map(obj -> {
                JsonObject r = (JsonObject) obj;

                return new UserFeedback(
                  UUID.fromString(r.getString("id")),
                  UUID.fromString(r.getString("user_id")),
                  UUID.fromString(r.getString("asset_id")),
                  r.getInteger("entity_rating"),
                  r.getString("action_subtype"),
                  r.getJsonObject("action_subdata"));
              })
              .toList();

          long total = rows.getTotalCount();

          return new UserFeedbackPaginatedResponse(
            result,
            PaginationInfo.from(page, size, total));
        });
  }

  @Override
  public Future<Boolean> deleteFeedback(UUID reqId, UUID userId) {

    String sql = """
      UPDATE user_interactions
      SET action_subtype = NULL,
          action_subdata = NULL,
          entity_rating  = NULL
      WHERE id = $1
      AND user_id = $2
      """;

    JsonArray params = new JsonArray()
      .add(reqId.toString())
      .add(userId.toString());

    LOGGER.debug("SQL: {}", sql);
    LOGGER.debug("Params: {}", params);

    return postgresService.executeQuery(sql, params).map(v -> true);
  }


}
