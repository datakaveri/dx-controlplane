package org.cdpg.dx.aaa.interaction.v2.dao.impl;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.interaction.dao.impl.UserInteractionDaoImpl;
import org.cdpg.dx.aaa.interaction.v2.dao.UserFeedbackDao;
import org.cdpg.dx.aaa.interaction.v2.model.FeedbackStatus;
import org.cdpg.dx.aaa.interaction.v2.model.RatingSummary;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedbackPaginatedResponse;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.TemporalRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class UserFeedbackDaoImpl extends AbstractBaseDAO<UserFeedback> implements UserFeedbackDao {

  private static final Logger LOGGER = LogManager.getLogger(UserInteractionDaoImpl.class);

  public UserFeedbackDaoImpl(PostgresService postgresService) {
    super(postgresService, "user_interactions", "id", UserFeedback::fromJson);
  }

  @Override
  public Future<UserFeedback> postFeedback(UserFeedback userFeedback) {

    JsonObject json = userFeedback.toJson();

    validateFeedback(json);

    String sql =
        """
        INSERT INTO user_interactions (
            user_id,
            asset_id,
            asset_type,
            entity_rating,
            action_subtype,
            action_subdata,
            feedback_created_at,
            feedback_updated_at,
            feedback_status,
            feedback_status_updated_at
        )
        VALUES (
            $1,
            $2,
            $3,
            $4,
            $5,
            $6,
            now(),
            now(),
            'PENDING',
            now()
        )

        ON CONFLICT (user_id, asset_id)
        DO NOTHING

        RETURNING
            id,
            user_id,
            asset_id,
            asset_type,
            entity_rating,
            action_subtype,
            action_subdata,
            feedback_created_at,
            feedback_updated_at,
            feedback_status,
            feedback_comment,
            feedback_status_updated_at
        """;

    JsonArray params =
        new JsonArray()
            .add(userFeedback.userId().toString())
            .add(userFeedback.assetId().toString())
            .add(userFeedback.assetType())
            .addNull()
            .addNull()
            .addNull();

    if (json.getInteger("entityRating") != null) {
      params.set(3, json.getInteger("entityRating"));
    }

    if (json.getString("actionSubtype") != null) {
      params.set(4, json.getString("actionSubtype"));
      params.set(5, json.getJsonObject("actionSubdata"));
    }

    return postgresService
        .executeQuery(sql, params)
        .compose(
            rows -> {
              if (!rows.getRows().isEmpty()) {
                return Future.succeededFuture(
                    UserFeedback.fromJson(rows.getRows().getJsonObject(0)));
              }

              return Future.failedFuture(
                  new DxConflictException(
                      "Feedback already exists for this asset. "
                          + "Use the PUT /iudx/v2/user/feedback API to update it."));
            })
        .onFailure(
            err ->
                LOGGER.error(
                    "Failed to post feedback for userId={}, assetId={}",
                    userFeedback.userId(),
                    userFeedback.assetId(),
                    err));
  }

  private void validateFeedback(JsonObject json) {

    boolean hasSubtype =
        json.containsKey("actionSubtype") && json.getString("actionSubtype") != null;

    boolean hasSubdata =
        json.containsKey("actionSubdata") && json.getJsonObject("actionSubdata") != null;

    if (hasSubtype && !hasSubdata) {
      throw new IllegalArgumentException(
          "actionSubdata is required when actionSubtype is provided");
    }

    if (!hasSubtype && hasSubdata) {
      throw new IllegalArgumentException(
          "actionSubtype is required when actionSubdata is provided");
    }

    Integer rating = json.getInteger("entityRating");

    if (rating != null && (rating < 1 || rating > 5)) {
      throw new IllegalArgumentException("entityRating must be between 1 and 5");
    }

    if (rating == null && !hasSubtype) {
      throw new IllegalArgumentException(
          "At least one of entityRating or actionSubtype/actionSubdata must be provided");
    }
  }

  @Override
  public Future<UserFeedback> putFeedback(UserFeedback userFeedback) {

    JsonObject json = userFeedback.toJson();

    validateFeedback(json);

    String sql =
        """
        UPDATE user_interactions
        SET
            asset_type = $3,
            entity_rating = $4,
            action_subtype = $5,
            action_subdata = $6,
            feedback_updated_at = now()
        WHERE
            user_id = $1
            AND asset_id = $2
            AND feedback_status = 'PENDING'

        RETURNING
            id,
            user_id,
            asset_id,
            asset_type,
            entity_rating,
            action_subtype,
            action_subdata,
            feedback_created_at,
            feedback_updated_at,
            feedback_status,
            feedback_comment,
            feedback_status_updated_at
        """;

    JsonArray params =
        new JsonArray()
            .add(userFeedback.userId().toString())
            .add(userFeedback.assetId().toString())
            .add(userFeedback.assetType())
            .addNull()
            .addNull()
            .addNull();

    if (json.getInteger("entityRating") != null) {
      params.set(3, json.getInteger("entityRating"));
    }

    if (json.getString("actionSubtype") != null) {
      params.set(4, json.getString("actionSubtype"));
      params.set(5, json.getJsonObject("actionSubdata"));
    }

    return postgresService
        .executeQuery(sql, params)
        .compose(
            rows -> {
              if (!rows.getRows().isEmpty()) {
                return Future.succeededFuture(
                    UserFeedback.fromJson(rows.getRows().getJsonObject(0)));
              }

              return getExistingFeedbackStatus(userFeedback.userId(), userFeedback.assetId())
                  .compose(
                      status -> {
                        if (status == null) {
                          return Future.failedFuture(
                              new DxForbiddenException(
                                  "Feedback not found for the specified asset"));
                        }

                        if ("APPROVED".equals(status)) {
                          return Future.failedFuture(
                              new DxForbiddenException(
                                  "Feedback cannot be updated because it has already been approved"));
                        }

                        if ("REJECTED".equals(status)) {
                          return Future.failedFuture(
                              new DxForbiddenException(
                                  "Feedback cannot be updated because it has already been rejected"));
                        }

                        return Future.failedFuture(
                            new DxForbiddenException(
                                "Feedback can only be updated while it is in PENDING status"));
                      });
            })
        .onFailure(
            err ->
                LOGGER.error(
                    "Failed to update feedback for userId={}, assetId={}",
                    userFeedback.userId(),
                    userFeedback.assetId(),
                    err));
  }

  private Future<String> getExistingFeedbackStatus(UUID userId, UUID assetId) {

    String sql =
        """
        SELECT feedback_status
        FROM user_interactions
        WHERE user_id = $1
          AND asset_id = $2
        """;

    JsonArray params = new JsonArray().add(userId.toString()).add(assetId.toString());

    return postgresService
        .executeQuery(sql, params)
        .map(
            rows -> {
              if (rows.getRows().isEmpty()) {
                return null;
              }

              return rows.getRows().getJsonObject(0).getString("feedback_status");
            })
        .onFailure(
            err ->
                LOGGER.error(
                    "Failed to getExistingFeedbackStatus for userId={}, assetId={}",
                    userId,
                    assetId,
                    err));
  }

  // Shared WHERE-clause builder for the paginated feedback list and the (page-invariant)
  // rating summary below — same filters, different base predicate and independent
  // placeholder numbering since each is run as its own prepared statement.
  private record FilterClause(String sql, JsonArray params, int nextIndex) {}

  private FilterClause buildFilterClause(
      String basePredicate, Map<String, Object> filters, List<TemporalRequest> temporalRequests) {

    StringBuilder where = new StringBuilder(" WHERE ").append(basePredicate);
    JsonArray params = new JsonArray();
    AtomicInteger index = new AtomicInteger(1);

    // helper to support both single value and list → IN clause
    java.util.function.BiConsumer<String, Object> applyFilter =
        (column, rawValue) -> {
          if (rawValue == null) return;

          List<?> values = rawValue instanceof List<?> list ? list : List.of(rawValue);

          if (values.isEmpty()) return;

          where.append(" AND ").append(column).append(" IN (");

          for (int i = 0; i < values.size(); i++) {
            if (i > 0) where.append(", ");

            where.append("$").append(index.getAndIncrement());

            Object value = values.get(i);

            if ("entity_rating".equals(column)) {
              params.add(Integer.valueOf(value.toString()));
            } else {
              params.add(value);
            }
          }

          where.append(")");
        };

    // Apply filters
    applyFilter.accept("user_id", filters.get("user_id"));
    applyFilter.accept("asset_id", filters.get("asset_id"));
    applyFilter.accept("action_subtype", filters.get("action_subtype"));
    applyFilter.accept("entity_rating", filters.get("entity_rating"));

    // Temporal filters
    // feedback_created_at is a dedicated column (distinct from the shared created_at used by
    // like/dislike/bookmark activity on the same row); only that field is exposed for temporal
    // filtering here, so any other timeField configured upstream is intentionally ignored.
    if (temporalRequests != null) {
      for (TemporalRequest tr : temporalRequests) {
        if (!"feedback_created_at".equals(tr.timeField())) continue;

        String rel = tr.timeRel() != null ? tr.timeRel().toLowerCase() : "";

        switch (rel) {
          case "before" -> {
            where.append(" AND feedback_created_at < $").append(index.getAndIncrement());
            params.add(tr.time());
          }
          case "after" -> {
            where.append(" AND feedback_created_at > $").append(index.getAndIncrement());
            params.add(tr.time());
          }
          case "between", "during" -> {
            where
                .append(" AND feedback_created_at BETWEEN $")
                .append(index.getAndIncrement())
                .append(" AND $")
                .append(index.getAndIncrement());

            params.add(tr.time());
            params.add(tr.endtime());
          }
          default -> {}
        }
      }
    }

    return new FilterClause(where.toString(), params, index.get());
  }

  private RatingSummary toRatingSummary(JsonObject row) {
    long total = row.getLong("total_ratings", 0L);
    if (total == 0) {
      return RatingSummary.empty();
    }

    double average = Math.round(row.getDouble("average_rating", 0.0) * 100.0) / 100.0;

    Map<String, Long> distribution = new LinkedHashMap<>();
    for (int star = 1; star <= 5; star++) {
      distribution.put(String.valueOf(star), row.getLong("rating_" + star, 0L));
    }

    return new RatingSummary(average, total, distribution);
  }

  @Override
  public Future<UserFeedbackPaginatedResponse> fetchPlatformUsersFeedbacks(
      PaginatedRequest request) {

    int page = request.page();
    int size = request.size();
    int offset = (page - 1) * size;

    Map<String, Object> filters = request.filters();
    LOGGER.debug("filters: {}", filters);

    List<TemporalRequest> temporalRequests = request.temporalRequests();

    // Only feedback_created_at is exposed for sorting today; whitelist defensively
    // rather than splicing an arbitrary column name into the SQL string.
    OrderBy orderBy =
        request.orderByList() != null && !request.orderByList().isEmpty()
            ? request.orderByList().getFirst()
            : null;

    String orderDirection =
        orderBy != null
                && "feedback_created_at".equals(orderBy.getColumn())
                && orderBy.getDirection() == OrderBy.Direction.ASC
            ? "ASC"
            : "DESC";

    // -----------------------------
    // Paginated feedback rows
    // -----------------------------
    // user_interactions is shared with like/dislike/bookmark tracking; only rows
    // that actually carry feedback (a rating and/or a structured action) belong
    // in this response, not every interaction row for the matched user/asset.
    FilterClause pageFilter =
        buildFilterClause(
            "(entity_rating IS NOT NULL OR action_subtype IS NOT NULL)", filters, temporalRequests);

    int limitIndex = pageFilter.nextIndex();
    int offsetIndex = limitIndex + 1;

    String sql =
        """
      SELECT
          id,
          user_id,
          asset_id,
          asset_type,
          entity_rating,
          action_subtype,
          action_subdata,
          feedback_created_at,
          feedback_updated_at,
          feedback_status,
          feedback_comment,
          feedback_status_updated_at,
          COUNT(*) OVER() AS total_count
      FROM user_interactions
      """
            + pageFilter.sql()
            + " ORDER BY feedback_created_at "
            + orderDirection
            + " NULLS LAST"
            + " LIMIT $"
            + limitIndex
            + " OFFSET $"
            + offsetIndex;

    JsonArray params = pageFilter.params().copy();
    params.add(size);
    params.add(offset);

    LOGGER.debug("SQL: {}", sql);
    LOGGER.debug("Params: {}", params);

    Future<PagedRows> pageFuture =
        postgresService
            .executeQuery(sql, params)
            .map(
                rows ->
                    new PagedRows(
                        rows.getRows().stream()
                            .map(obj -> UserFeedback.fromJson((JsonObject) obj))
                            .toList(),
                        rows.getTotalCount()));

    // -----------------------------
    // Full-set rating summary (never page-scoped, so no LIMIT/OFFSET here)
    // -----------------------------
    FilterClause summaryFilter = buildFilterClause("entity_rating IS NOT NULL", filters, temporalRequests);

    String summarySql =
        """
      SELECT
          COUNT(*) AS total_ratings,
          COALESCE(AVG(entity_rating), 0)::double precision AS average_rating,
          COUNT(*) FILTER (WHERE entity_rating = 1) AS rating_1,
          COUNT(*) FILTER (WHERE entity_rating = 2) AS rating_2,
          COUNT(*) FILTER (WHERE entity_rating = 3) AS rating_3,
          COUNT(*) FILTER (WHERE entity_rating = 4) AS rating_4,
          COUNT(*) FILTER (WHERE entity_rating = 5) AS rating_5
      FROM user_interactions
      """
            + summaryFilter.sql();

    LOGGER.debug("Summary SQL: {}", summarySql);
    LOGGER.debug("Summary Params: {}", summaryFilter.params());

    Future<RatingSummary> summaryFuture =
        postgresService
            .executeQuery(summarySql, summaryFilter.params())
            .map(rows -> toRatingSummary(rows.getRows().getJsonObject(0)));

    List<Future> futures = List.of(pageFuture, summaryFuture);
    return CompositeFuture.all(futures)
        .map(
            cf -> {
              PagedRows paged = pageFuture.result();
              return new UserFeedbackPaginatedResponse(
                  paged.data(),
                  summaryFuture.result(),
                  PaginationInfo.from(page, size, paged.total()));
            });
  }

  @Override
  public Future<UserFeedbackPaginatedResponse> fetchUserFeedbacks(PaginatedRequest request) {

    int page = request.page();
    int size = request.size();
    int offset = (page - 1) * size;

    Map<String, Object> filters = request.filters();
    LOGGER.debug("filters: {}", filters);

    List<TemporalRequest> temporalRequests = request.temporalRequests();

    // Only feedback_created_at is exposed for sorting today; whitelist defensively
    // rather than splicing an arbitrary column name into the SQL string.
    OrderBy orderBy =
        request.orderByList() != null && !request.orderByList().isEmpty()
            ? request.orderByList().getFirst()
            : null;

    String orderDirection =
        orderBy != null
                && "feedback_created_at".equals(orderBy.getColumn())
                && orderBy.getDirection() == OrderBy.Direction.ASC
            ? "ASC"
            : "DESC";

    // -----------------------------
    // Paginated feedback rows
    // -----------------------------
    // user_interactions is shared with like/dislike/bookmark tracking; only rows
    // that actually carry feedback (a rating and/or a structured action) belong
    // in this response, not every interaction row for the matched user/asset.
    FilterClause pageFilter =
        buildFilterClause(
            """
            (
                entity_rating IS NOT NULL
                OR action_subtype IS NOT NULL
            )
            """,
            filters,
            temporalRequests);

    int limitIndex = pageFilter.nextIndex();
    int offsetIndex = limitIndex + 1;

    String sql =
        """
      SELECT
          id,
          user_id,
          asset_id,
          asset_type,
          entity_rating,
          action_subtype,
          action_subdata,
          feedback_created_at,
          feedback_updated_at,
          feedback_status,
          feedback_comment,
          feedback_status_updated_at,
          COUNT(*) OVER() AS total_count
      FROM user_interactions
      """
            + pageFilter.sql()
            + " ORDER BY feedback_created_at "
            + orderDirection
            + " NULLS LAST"
            + " LIMIT $"
            + limitIndex
            + " OFFSET $"
            + offsetIndex;

    JsonArray params = pageFilter.params().copy();
    params.add(size);
    params.add(offset);

    LOGGER.debug("SQL: {}", sql);
    LOGGER.debug("Params: {}", params);

    Future<PagedRows> pageFuture =
        postgresService
            .executeQuery(sql, params)
            .map(
                rows ->
                    new PagedRows(
                        rows.getRows().stream()
                            .map(obj -> UserFeedback.fromJson((JsonObject) obj))
                            .toList(),
                        rows.getTotalCount()));

    // -----------------------------
    // Full-set rating summary (never page-scoped, so no LIMIT/OFFSET here)
    // -----------------------------
    FilterClause summaryFilter =
        buildFilterClause(
            """
            entity_rating IS NOT NULL
            AND feedback_status = 'APPROVED'
            """,
            filters,
            temporalRequests);

    String summarySql =
        """
      SELECT
          COUNT(*) AS total_ratings,
          COALESCE(AVG(entity_rating), 0)::double precision AS average_rating,
          COUNT(*) FILTER (WHERE entity_rating = 1) AS rating_1,
          COUNT(*) FILTER (WHERE entity_rating = 2) AS rating_2,
          COUNT(*) FILTER (WHERE entity_rating = 3) AS rating_3,
          COUNT(*) FILTER (WHERE entity_rating = 4) AS rating_4,
          COUNT(*) FILTER (WHERE entity_rating = 5) AS rating_5
      FROM user_interactions
      """
            + summaryFilter.sql();

    LOGGER.debug("Summary SQL: {}", summarySql);
    LOGGER.debug("Summary Params: {}", summaryFilter.params());

    Future<RatingSummary> summaryFuture =
        postgresService
            .executeQuery(summarySql, summaryFilter.params())
            .map(rows -> toRatingSummary(rows.getRows().getJsonObject(0)));

    List<Future> futures = List.of(pageFuture, summaryFuture);
    return CompositeFuture.all(futures)
        .map(
            cf -> {
              PagedRows paged = pageFuture.result();
              return new UserFeedbackPaginatedResponse(
                  paged.data(),
                  summaryFuture.result(),
                  PaginationInfo.from(page, size, paged.total()));
            });
  }

  private record PagedRows(List<UserFeedback> data, long total) {}

  @Override
  public Future<Boolean> deleteFeedback(UUID userId, UUID assetId) {

    String sql =
        """
      UPDATE user_interactions
      SET action_subtype      = NULL,
          action_subdata      = NULL,
          entity_rating       = NULL,
          feedback_created_at = NULL,
          feedback_updated_at = NULL,
          feedback_status = NULL,
          feedback_comment = NULL,
          feedback_status_updated_at = NULL
      WHERE user_id = $1
      AND asset_id = $2
      AND (
              entity_rating IS NOT NULL
              OR action_subtype IS NOT NULL
          )
      """;

    JsonArray params = new JsonArray().add(userId.toString()).add(assetId.toString());

    LOGGER.debug("SQL: {}", sql);
    LOGGER.debug("Params: {}", params);

    return postgresService.executeQuery(sql, params).map(QueryResult::isRowsAffected);
  }

  @Override
  public Future<UserFeedback> updateFeedbackStatus(
      UUID feedbackId, FeedbackStatus status, String comment) {

    String sql =
        """
        UPDATE user_interactions
        SET
            feedback_status = $2,
            feedback_comment = $3,
            feedback_status_updated_at = now()
        WHERE
            id = $1
            AND (
                entity_rating IS NOT NULL
                OR action_subtype IS NOT NULL
            )
            AND feedback_status = 'PENDING'
        RETURNING
            id,
            user_id,
            asset_id,
            asset_type,
            entity_rating,
            action_subtype,
            action_subdata,
            feedback_created_at,
            feedback_updated_at,
            feedback_status,
            feedback_comment,
            feedback_status_updated_at
        """;

    JsonArray params = new JsonArray().add(feedbackId.toString()).add(status.name()).add(comment);

    LOGGER.debug("SQL: {}", sql);
    LOGGER.debug("Params: {}", params);

    return postgresService
        .executeQuery(sql, params)
        .compose(
            rows -> {
              if (rows.getRows().isEmpty()) {

                return Future.failedFuture(new DxNotFoundException("Pending feedback not found"));
              }

              return Future.succeededFuture(UserFeedback.fromJson(rows.getRows().getJsonObject(0)));
            });
  }

  @Override
  public Future<UserFeedbackPaginatedResponse> fetchApprovedPlatformUserFeedbacks(
      PaginatedRequest request) {

    int page = request.page();
    int size = request.size();
    int offset = (page - 1) * size;

    Map<String, Object> filters = request.filters();
    LOGGER.debug("Approved feedback filters: {}", filters);

    List<TemporalRequest> temporalRequests = request.temporalRequests();

    OrderBy orderBy =
        request.orderByList() != null && !request.orderByList().isEmpty()
            ? request.orderByList().getFirst()
            : null;

    String orderDirection =
        orderBy != null
                && "feedback_created_at".equals(orderBy.getColumn())
                && orderBy.getDirection() == OrderBy.Direction.ASC
            ? "ASC"
            : "DESC";

    // ---------------------------------------------------------
    // Paginated feedback rows
    // ---------------------------------------------------------
    //
    // Only return actual feedback rows and only rows that have
    // been approved for consumer visibility.
    //
    FilterClause pageFilter =
        buildFilterClause(
            """
            (entity_rating IS NOT NULL OR action_subtype IS NOT NULL)
            AND feedback_status = 'APPROVED'
            """,
            filters,
            temporalRequests);

    int limitIndex = pageFilter.nextIndex();
    int offsetIndex = limitIndex + 1;

    String sql =
        """
        SELECT
            id,
            user_id,
            asset_id,
            asset_type,
            entity_rating,
            action_subtype,
            action_subdata,
            feedback_created_at,
            feedback_updated_at,
            feedback_status,
            feedback_comment,
            feedback_status_updated_at,
            COUNT(*) OVER() AS total_count
        FROM user_interactions
        """
            + pageFilter.sql()
            + " ORDER BY feedback_created_at "
            + orderDirection
            + " NULLS LAST"
            + " LIMIT $"
            + limitIndex
            + " OFFSET $"
            + offsetIndex;

    JsonArray params = pageFilter.params().copy();
    params.add(size);
    params.add(offset);

    LOGGER.debug("Approved feedback SQL: {}", sql);
    LOGGER.debug("Approved feedback params: {}", params);

    Future<PagedRows> pageFuture =
        postgresService
            .executeQuery(sql, params)
            .map(
                rows ->
                    new PagedRows(
                        rows.getRows().stream()
                            .map(obj -> UserFeedback.fromJson((JsonObject) obj))
                            .toList(),
                        rows.getTotalCount()));

    // ---------------------------------------------------------
    // Full-set rating summary
    // ---------------------------------------------------------
    FilterClause summaryFilter =
        buildFilterClause(
            """
            entity_rating IS NOT NULL
            AND feedback_status = 'APPROVED'
            """,
            filters,
            temporalRequests);

    String summarySql =
        """
        SELECT
            COUNT(*) AS total_ratings,
            COALESCE(AVG(entity_rating), 0)::double precision AS average_rating,
            COUNT(*) FILTER (WHERE entity_rating = 1) AS rating_1,
            COUNT(*) FILTER (WHERE entity_rating = 2) AS rating_2,
            COUNT(*) FILTER (WHERE entity_rating = 3) AS rating_3,
            COUNT(*) FILTER (WHERE entity_rating = 4) AS rating_4,
            COUNT(*) FILTER (WHERE entity_rating = 5) AS rating_5
        FROM user_interactions
        """
            + summaryFilter.sql();

    LOGGER.debug("Approved feedback summary SQL: {}", summarySql);
    LOGGER.debug("Approved feedback summary params: {}", summaryFilter.params());

    Future<RatingSummary> summaryFuture =
        postgresService
            .executeQuery(summarySql, summaryFilter.params())
            .map(rows -> toRatingSummary(rows.getRows().getJsonObject(0)));

    return Future.all(List.of(pageFuture, summaryFuture))
        .map(
            cf -> {
              PagedRows paged = pageFuture.result();

              return new UserFeedbackPaginatedResponse(
                  paged.data(),
                  summaryFuture.result(),
                  PaginationInfo.from(page, size, paged.total()));
            });
  }
}
