package org.cdpg.dx.aaa.summary.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.summary.dao.UsageAggregationDao;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.List;

public class UsageAggregationDaoImpl implements UsageAggregationDao {

  private static final Logger LOGGER = LogManager.getLogger(UsageAggregationDaoImpl.class);

  private final PostgresService postgresService;
  private final String activityTable;
  private final List<String> excludedUserIds;

  public UsageAggregationDaoImpl(
      PostgresService postgresService, String activityTable, List<String> excludedUserIds) {

    this.postgresService = postgresService;
    this.activityTable = activityTable;
    this.excludedUserIds = excludedUserIds != null ? excludedUserIds : List.of();
  }

  @Override
  public Future<JsonObject> aggregateUsage() {

    StringBuilder sqlBuilder =
        new StringBuilder(
            """
                SELECT
                  COUNT(*) AS total_count,

                  COUNT(*) FILTER (WHERE created_at >= now() - interval '1 year')   AS year_count,
                  COUNT(*) FILTER (WHERE created_at >= now() - interval '6 months') AS six_month_count,
                  COUNT(*) FILTER (WHERE created_at >= now() - interval '1 month')  AS one_month_count,
                  COUNT(*) FILTER (WHERE created_at >= now() - interval '7 days')   AS one_week_count,
                  COUNT(*) FILTER (WHERE created_at >= now() - interval '1 day')    AS one_day_count,

                  COALESCE(SUM(size_bytes), 0) AS total_size,

                  COALESCE(SUM(size_bytes) FILTER (WHERE created_at >= now() - interval '1 year'), 0)   AS year_size,
                  COALESCE(SUM(size_bytes) FILTER (WHERE created_at >= now() - interval '6 months'), 0) AS six_month_size,
                  COALESCE(SUM(size_bytes) FILTER (WHERE created_at >= now() - interval '1 month'), 0)  AS one_month_size,
                  COALESCE(SUM(size_bytes) FILTER (WHERE created_at >= now() - interval '7 days'), 0)   AS one_week_size,
                  COALESCE(SUM(size_bytes) FILTER (WHERE created_at >= now() - interval '1 day'), 0)    AS one_day_size
                FROM %s
                WHERE entity_type IN ('DATABANK', 'AI_MODEL')
                  AND operation IN ('VIEW', 'DOWNLOAD')
                """
                .formatted(activityTable));

    JsonArray params = new JsonArray();

    // Add excluded users if present
    if (excludedUserIds != null && !excludedUserIds.isEmpty()) {

      sqlBuilder.append("\n  AND user_id NOT IN (");

      for (int i = 0; i < excludedUserIds.size(); i++) {
        if (i > 0) {
          sqlBuilder.append(", ");
        }
        sqlBuilder.append("$").append(i + 1);
        params.add(excludedUserIds.get(i));
      }

      sqlBuilder.append(")");
    }

    String sql = sqlBuilder.toString();

    LOGGER.info("Prepared SQL: {} | With parameters: {}", sql, params.encodePrettily());

    return postgresService.executeQuery(sql, params).map(res -> res.getRows().getJsonObject(0));
  }
}
