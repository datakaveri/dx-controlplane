package org.cdpg.dx.database.postgres.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.util.DxPgExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostgresServiceImpl implements PostgresService {
  private static final Logger LOG = LoggerFactory.getLogger(PostgresServiceImpl.class);
  private final Pool client;

  public PostgresServiceImpl(Pool client) {
    this.client = client;
  }

  private QueryResult convertToQueryResult(RowSet<Row> rowSet) {
    LOG.trace("convertToQueryResult() invoked");
    JsonArray jsonArray = new JsonArray();
    Object value;
    for (Row row : rowSet) {
      JsonObject json = new JsonObject();
      for (int i = 0; i < row.size(); i++) {
        // json.put(row.getColumnName(i), row.getValue(i));
        // LOG.info("Column name: {}, value: {}", row.getColumnName(i), row.getValue(i));
        String column = row.getColumnName(i);
        value = row.getValue(i);
        if (value == null
            || value instanceof String
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof JsonObject
            || value instanceof JsonArray) {
          //  LOG.info("value:" + value);
          json.put(column, value);
        } else {
          json.put(column, value.toString());
        }
      }
      jsonArray.add(json);
    }

    boolean rowsAffected = rowSet.rowCount() > 0; // Check if any rows were affected
    if (rowsAffected) {
      LOG.info("Rows affected :{}", rowSet.rowCount());
    } else {
      LOG.info("Rows unaffected");
    }
    // LOG.info("Returned rows: {}", jsonArray.encodePrettily());

    QueryResult queryResult = new QueryResult();
    queryResult.setRows(jsonArray);
    queryResult.setTotalCount(rowSet.rowCount());
    queryResult.setHasMore(false);
    queryResult.setRowsAffected(rowsAffected);
    // return new QueryResult(jsonArray, jsonArray.size(), false, rowsAffected);
    return queryResult;
  }

  private Future<QueryResult> executeQuery(String sql, List<Object> params) {
    String formattedParams =
        IntStream.range(0, params.size())
            .mapToObj(i -> "param" + (i + 1) + "=" + params.get(i))
            .collect(Collectors.joining(", "));

    LOG.info("Executing SQL: {} | With parameters: {}", sql, formattedParams);
    Tuple tuple = Tuple.tuple();

    try {

      for (Object param : params) {
        /* LOG.info(
        "Param type: "
            + (param != null ? param.getClass().getSimpleName() : "null")
            + ", value: "
            + param);*/

        if (param instanceof String paramStr) {
          // Check if it's an ISO timestamp string
          if (paramStr.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$")
              || paramStr.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$")
              || paramStr.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}$")) {
            try {
              // Parse and
              // qconvert to LocalDateTime
              LocalDateTime time = LocalDateTime.parse(paramStr);

              tuple.addValue(time);
              continue;
            } catch (Exception e) {
              LOG.info("Failed to parse timestamp, keeping as string: " + paramStr);
            }
          }
        }
        // Default: keep original
        tuple.addValue(param);
      }

      return client
          .preparedQuery(sql)
          .execute(tuple)
          .map(
              rowSet -> {
                LOG.info("Query executed successfully.");
                return convertToQueryResult(rowSet);
              })
          .recover(
              err -> {
                LOG.error("SQL execution error: {}", err.getMessage());
                return Future.failedFuture(DxPgExceptionMapper.from(err));
              });

    } catch (Exception e) {
      LOG.error("Exception while building Tuple or executing query: {}", e.getMessage());
      return Future.failedFuture(DxPgExceptionMapper.from(e));
    }
  }

  @Override
  public Future<QueryResult> insert(InsertQuery query) {
    return executeQuery(query.toSQL(), query.getQueryParams());
  }

  @Override
  public Future<QueryResult> update(UpdateQuery query) {
    return executeQuery(query.toSQL(), query.getQueryParams());
  }

  @Override
  public Future<QueryResult> delete(DeleteQuery query) {
    return executeQuery(query.toSQL(), query.getQueryParams());
  }

  @Override
  public Future<QueryResult> select(SelectQuery query, boolean isCountQueryEnabled) {
    LOG.info("Executing select query: {}", query.toSQL());
    String sql = query.toSQL();
    if (isCountQueryEnabled) {
      // Insert COUNT(*) OVER() AS total_count into the select columns
      int selectIndex = sql.toLowerCase().indexOf("select") + 6;
      sql =
          sql.substring(0, selectIndex)
              + " COUNT(*) OVER() AS total_result_count,"
              + sql.substring(selectIndex);
    }
    return executeQuery(sql, query.getQueryParams())
        .map(
            result -> {
              if (isCountQueryEnabled && !result.getRows().isEmpty()) {
                int totalCount =
                    result.getRows().getJsonObject(0).getInteger("total_result_count", 0);
                result.setTotalCount(totalCount);
                // Optionally, remove total_count from each row if not needed in the output
                /*for (int i = 0; i < result.getRows().size(); i++) {
                  result.getRows().getJsonObject(i).remove("total_count");
                }*/
              }
              return result;
            });
  }

  @Override
  public Future<Boolean> ping() {
    Promise<Boolean> promise = Promise.promise();
    client
        .query("SELECT 1")
        .execute(
            ar -> {
              if (ar.succeeded()) {
                promise.complete(true);
              } else {
                promise.complete(false);
              }
            });
    return promise.future();
  }

  @Override
  public Future<QueryResult> upsert(UpsertQuery query) {
    return executeQuery(query.toSQL(), query.getQueryParams());
  }

  @Override
  public Future<QueryResult> executeQuery(String sql, JsonArray params) {

    Tuple tuple = Tuple.tuple();
    for (Object value : params) {
      tuple.addValue(value);
    }
    return client
        .preparedQuery(sql)
        .execute(tuple)
        .map(this::convertToQueryResult)
        .recover(err -> Future.failedFuture(DxPgExceptionMapper.from(err)));
  }
}
