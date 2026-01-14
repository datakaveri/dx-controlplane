package org.cdpg.dx.aaa.summary.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class UsageSummaryUpdaterDao {
  private static final Logger LOGGER = LogManager.getLogger(UsageSummaryUpdaterDao.class);

  private final PostgresService postgresService;
  private final String summaryTable;

  public UsageSummaryUpdaterDao(PostgresService postgresService, String summaryTable) {
    this.postgresService = postgresService;
    this.summaryTable = summaryTable;
  }

  public Future<Void> upsert(JsonObject row) {

    String sql =
        """
        INSERT INTO %s (description, count, size)
        VALUES
          ($1,  $2,  $3),
          ($4,  $5,  $6),
          ($7,  $8,  $9),
          ($10, $11, $12),
          ($13, $14, $15),
          ($16, $17, $18)
        ON CONFLICT (description)
        DO UPDATE SET
          count = EXCLUDED.count,
          size = EXCLUDED.size,
          updated_at = now()
        """
            .formatted(summaryTable);

    JsonArray params =
        new JsonArray()
            .add("Total usage since the beginning")
            .add(row.getLong("total_count", 0L))
            .add(row.getLong("total_size", 0L))
            .add("Total usage for the last 1 Year")
            .add(row.getLong("year_count", 0L))
            .add(row.getLong("year_size", 0L))
            .add("Total usage for the last 6 months")
            .add(row.getLong("six_month_count", 0L))
            .add(row.getLong("six_month_size", 0L))
            .add("Total usage for the last 1 month")
            .add(row.getLong("one_month_count", 0L))
            .add(row.getLong("one_month_size", 0L))
            .add("Total usage for the last 1 week")
            .add(row.getLong("one_week_count", 0L))
            .add(row.getLong("one_week_size", 0L))
            .add("Total usage of Yesterday")
            .add(row.getLong("one_day_count", 0L))
            .add(row.getLong("one_day_size", 0L));

    LOGGER.info(
        "Upserting SQL:  {} | usage summary with params: {} ", sql, params.encodePrettily());

    return postgresService.executeQuery(sql, params).mapEmpty();
  }
}
