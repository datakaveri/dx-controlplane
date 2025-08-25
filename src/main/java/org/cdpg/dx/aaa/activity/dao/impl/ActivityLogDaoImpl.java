package org.cdpg.dx.aaa.activity.dao.impl;

import static org.cdpg.dx.aaa.activity.util.ActivityConstants.*;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.activity.dao.ActivityLogDao;
import org.cdpg.dx.aaa.activity.model.ActivityLog;
import org.cdpg.dx.aaa.activity.model.ActivityLogRequest;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.database.postgres.util.DxPgExceptionMapper;

public class ActivityLogDaoImpl extends AbstractBaseDAO<ActivityLog> implements ActivityLogDao {

  private static final Logger LOGGER = LogManager.getLogger(ActivityLogDaoImpl.class);

  private static final List<String> MINIMAL_COLUMNS =
      List.of("id", "user_id", "asset_name", "operation");

  public ActivityLogDaoImpl(PostgresService postgresService) {
    super(postgresService, ACTIVITY_LOG_TABLE_NAME, ID, ActivityLog::fromJson);
  }

  @Override
  public Future<PaginatedResult<ActivityLog>> getAllActivityLogsByUserId(ActivityLogRequest req) {
    LOGGER.info("getAllActivityLogsByUserId() started with request: {}", req.getUserId());

    int page = req.getPage() > 0 ? req.getPage() : 1;
    int size = req.getSize() > 0 ? req.getSize() : 10;
    int offset = (page - 1) * size;

    List<Condition> conditions = new ArrayList<>();

    if (req.getUserId() != null) {
      conditions.add(
          new Condition(USER_ID, Condition.Operator.EQUALS, List.of(req.getUserId().toString())));
    }

    if (req.getStartTime() != null) {
      conditions.add(
          new Condition(
              CREATED_AT, Condition.Operator.GREATER_EQUALS, List.of(req.getStartTime())));
    }

    if (req.getEndTime() != null) {
      conditions.add(
          new Condition(CREATED_AT, Condition.Operator.LESS_EQUALS, List.of(req.getEndTime())));
    }

    Condition finalCondition =
        conditions.isEmpty() ? null : new Condition(conditions, Condition.LogicalOperator.AND);

    LOGGER.debug("finalCondition: {}", finalCondition);

    SelectQuery query =
        new SelectQuery(
            ACTIVITY_LOG_TABLE_NAME, List.of("*"), finalCondition, null, null, size, offset);

    return postgresService
        .select(query, true)
        .map(result -> toPaginatedResult(result, page, size))
        .recover(
            err -> {
              LOGGER.error(
                  "Error fetching all from: {}, msg: {}", tableName, err.getMessage(), err);
              return Future.failedFuture(BaseDxException.from(err));
            })
        .recover(
            err -> {
              LOGGER.error("Error fetching paginated activity logs: {}", err.getMessage(), err);
              return Future.failedFuture(DxPgExceptionMapper.from(err));
            });
  }

  @Override
  public Future<ActivityLog> createActivityLog(ActivityLog activityLogEntity) {
    LOGGER.debug("createActivityLog() called with entity: {}", activityLogEntity);
    return create(activityLogEntity);
  }

  @Override
  public Future<PaginatedResult<ActivityLog>> getAllActivityLogsForAdmin(ActivityLogRequest req) {
    LOGGER.info("getAllActivityLogsForAdmin() called");

    int page = req.getPage() > 0 ? req.getPage() : 1;
    int size = req.getSize() > 0 ? req.getSize() : 10;
    int offset = (page - 1) * size;

    List<Condition> conditions = new ArrayList<>();

    if (req.getUserId() != null) {
      conditions.add(
          new Condition(USER_ID, Condition.Operator.EQUALS, List.of(req.getUserId().toString())));
    }

    if (req.getStartTime() != null) {
      conditions.add(
          new Condition(
              CREATED_AT, Condition.Operator.GREATER_EQUALS, List.of(req.getStartTime())));
    }

    if (req.getEndTime() != null) {
      conditions.add(
          new Condition(CREATED_AT, Condition.Operator.LESS_EQUALS, List.of(req.getEndTime())));
    }

    Condition finalCondition =
        conditions.isEmpty() ? null : new Condition(conditions, Condition.LogicalOperator.AND);

    LOGGER.debug("finalCondition: {}", finalCondition);

    SelectQuery query =
        new SelectQuery(
            ACTIVITY_LOG_TABLE_NAME, List.of("*"), finalCondition, null, null, size, offset);

    LOGGER.debug("query columns: {}", query.getColumns());
    LOGGER.debug("query params: {}", query.getQueryParams());

    return postgresService
        .select(query, true)
        .map(result -> toPaginatedResult(result, page, size))
        .recover(
            err -> {
              LOGGER.error(
                  "Error fetching all from: {}, msg: {}", tableName, err.getMessage(), err);
              return Future.failedFuture(BaseDxException.from(err));
            })
        .recover(
            err -> {
              LOGGER.error("Error fetching paginated activity logs: {}", err.getMessage(), err);
              return Future.failedFuture(DxPgExceptionMapper.from(err));
            });
  }

  /**
   * Maps a JsonArray of rows to a List of ActivityLog objects.
   *
   * @param rows The JsonArray containing the rows to be mapped.
   * @return A List of ActivityLog objects.
   */
  private List<ActivityLog> mapToActivityLogs(JsonArray rows) {
    return rows.stream()
        .map(obj -> ActivityLog.fromJson((JsonObject) obj))
        .collect(Collectors.toList());
  }

  private PaginatedResult<ActivityLog> toPaginatedResult(QueryResult result, int page, int size) {
    List<ActivityLog> entities =
        result.getRows().stream()
            .map(row -> fromJson.apply((JsonObject) row))
            .collect(Collectors.toList());
    long totalCount = result.getTotalCount();
    int totalPages = (int) Math.ceil((double) totalCount / size);
    boolean hasNext = page < totalPages;
    boolean hasPrevious = page > 1;

    PaginationInfo paginationInfo =
        new PaginationInfo(page, size, totalCount, totalPages, hasNext, hasPrevious);
    return new PaginatedResult<>(paginationInfo, entities);
  }
}
