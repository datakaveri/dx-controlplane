package org.cdpg.dx.aaa.interaction.v2.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

public class UserFeedbackDaoImpl extends AbstractBaseDAO<UserFeedback>
  implements UserFeedbackDao {

  private static final Logger LOGGER = LogManager.getLogger(UserInteractionDaoImpl.class);

  public UserFeedbackDaoImpl(PostgresService postgresService) {
    super(postgresService, "user_interactions", "id", UserFeedback::fromJson);
  }

  @Override
  public Future<UserFeedback> postFeedback(UserFeedback userFeedback) {
    JsonObject json = userFeedback.toJson();

    String userId = json.getString("userId");
    String assetId = json.getString("assetId");

    boolean hasSubtype =
        json.containsKey("actionSubtype") && json.getString("actionSubtype") != null;

    boolean hasSubdata =
        json.containsKey("actionSubdata") && json.getJsonObject("actionSubdata") != null;

    if (hasSubtype && !hasSubdata) {
      return Future.failedFuture(
          new IllegalArgumentException(
              "actionSubdata is required when actionSubtype is provided"));
    }

    if (!hasSubtype && hasSubdata) {
      return Future.failedFuture(
          new IllegalArgumentException(
              "actionSubtype is required when actionSubdata is provided"));
    }

    Integer rating = json.getInteger("entityRating");

    if (rating != null && (rating < 1 || rating > 5)) {
      return Future.failedFuture(
          new IllegalArgumentException("entityRating must be between 1 and 5"));
    }

    var map = userFeedback.toNonEmptyFieldsMap();

    Condition condition =
        new Condition(
            List.of(
                new Condition("user_id", Condition.Operator.EQUALS, List.of(userId)),
                new Condition("asset_id", Condition.Operator.EQUALS, List.of(assetId))),
            Condition.LogicalOperator.AND);

    SelectQuery selectQuery =
        new SelectQuery()
            .setTable("user_interactions")
            .setColumns(List.of("*"))
            .setCondition(condition);

    return postgresService
        .select(selectQuery, true)
        .compose(
            result -> {
              if (!result.isRowsAffected()) {
                // No existing feedback → INSERT
                InsertQuery insertQuery =
                    new InsertQuery(
                        "user_interactions", List.copyOf(map.keySet()), List.copyOf(map.values()));

                return postgresService
                    .insert(insertQuery)
                    .map(res -> UserFeedback.fromJson(res.toJson()));

              } else {
                // Existing feedback → UPDATE
                UpdateQuery updateQuery =
                    new UpdateQuery(
                        "user_interactions",
                        List.copyOf(map.keySet()),
                        List.copyOf(map.values()),
                        condition,
                        null,
                        null);

                return postgresService
                    .update(updateQuery)
                    .map(res -> UserFeedback.fromJson(res.toJson()));
              }
            });
  }

  @Override
  public Future<UserFeedback> updateFeedback(UserFeedback userFeedback) {
    JsonObject json = userFeedback.toJson();

    String userId = json.getString("userId");
    String assetId = json.getString("assetId");

    String subtype = json.getString("actionSubtype");
    JsonObject subdata = json.getJsonObject("actionSubdata");

    boolean hasSubtype =
        json.containsKey("actionSubtype") && json.getString("actionSubtype") != null;
    boolean hasSubdata =
        json.containsKey("actionSubdata") && json.getJsonObject("actionSubdata") != null;

    if (hasSubtype && !hasSubdata) {
      return Future.failedFuture(
        new IllegalArgumentException("actionSubdata is required when actionSubtype is provided"));
    }

    if (!hasSubtype && hasSubdata) {
      return Future.failedFuture(
        new IllegalArgumentException("actionSubtype is required when actionSubdata is provided"));
    }

    Integer rating = json.getInteger("entityRating");

    if (rating != null && (rating < 1 || rating > 5)) {
      return Future.failedFuture(
        new IllegalArgumentException(
          "entityRating must be between 1 and 5"
        )
      );
    }

    List<String> columns = new ArrayList<>();
    List<Object> values = new ArrayList<>();

    if (json.containsKey("actionSubtype")) {
      columns.add("actionSubtype");
      values.add(json.getString("actionSubtype"));
    }

    if (json.containsKey("actionSubdata")) {
      columns.add("actionSubdata");
      values.add(json.getJsonObject("actionSubdata"));
    }

    if (json.containsKey("entityRating")) {
      columns.add("entityRating");
      values.add(json.getInteger("entityRating"));
    }

    if (columns.isEmpty()) {
      return Future.succeededFuture(userFeedback); // nothing to update
    }

    Condition condition =
      new Condition(
        List.of(
          new Condition("userId", Condition.Operator.EQUALS, List.of(userId)),
          new Condition("assetId", Condition.Operator.EQUALS, List.of(assetId))
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
    applyFilter.accept("user_id", filters.get("userId"));
    applyFilter.accept("asset_id", filters.get("assetId"));
    applyFilter.accept("action_subtype", filters.get("actionSubtype"));

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
                  r.getString("asset_type"),
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
