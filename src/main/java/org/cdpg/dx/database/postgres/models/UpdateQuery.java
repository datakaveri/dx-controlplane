package org.cdpg.dx.database.postgres.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@DataObject(generateConverter = true)
public class UpdateQuery implements Query {
  private static final Logger LOGGER = LogManager.getLogger(UpdateQuery.class);
  private String table;
  private List<String> columns;
  private List<Object> values;
  private Condition condition;
  private List<OrderBy> orderBy;
  private Integer limit;

  public UpdateQuery(
      String table,
      List<String> columns,
      List<Object> values,
      Condition condition,
      List<OrderBy> orderBy,
      Integer limit) {
    this.table = Objects.requireNonNull(table, "Table name cannot be null");
    this.columns = Objects.requireNonNull(columns, "Columns cannot be null");
    this.values = Objects.requireNonNull(values, "Values cannot be null");
    this.condition = condition;
    this.orderBy = orderBy;
    this.limit = limit;
  }

  public UpdateQuery(UpdateQuery other) {
    this.table = other.getTable();
    this.columns = other.getColumns();
    this.values = other.getValues();
    this.condition = other.getCondition();
    this.orderBy = other.getOrderBy();
    this.limit = other.getLimit();
  }
  public UpdateQuery(){}

  public UpdateQuery(JsonObject json) {
    UpdateQueryConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    UpdateQueryConverter.toJson(this, json);
    return json;
  }

//    @Override
//    public String toSQL() {
//        StringBuilder query = new StringBuilder("UPDATE ").append(table).append(" SET ");
//        query.append(columns.stream().map(column -> column + " = ?").collect(Collectors.joining(", ")));
//
//        if (condition != null) query.append(" WHERE ").append(condition.toSQL());
//
//        if (orderBy != null && !orderBy.isEmpty()) {
//            query.append(" ORDER BY ")
//                    .append(orderBy.stream().map(OrderBy::toSQL).collect(Collectors.joining(", ")));
//        }
//        if (limit != null) query.append(" LIMIT ").append(limit);
//
//        return query.toString();
//    }

  /* @Override
  public String toSQL() {
    StringBuilder query = new StringBuilder("UPDATE ").append(table).append(" SET ");

    // Use indexed placeholders for each SET column: $1, $2, $3, ...
    for (int i = 0; i < columns.size(); i++) {
      query.append(columns.get(i)).append(" = $").append(i + 1);
      if (i < columns.size() - 1) {
        query.append(", ");
      }
    }

    // Append condition if present, adjusting its placeholder index
    if (condition != null) {
      // Hacky but safe: replace $1 in condition SQL with correct index
      int condParamIndex = columns.size() + 1;
      //String condSql = condition.toSQL().replace("$1", "$" + condParamIndex);
        // Rewrite condition SQL with updated parameter indices
        String condSql = condition.toSQL();
        List<Object> condParams = condition.getQueryParams();
        for (int i = 0; i < condParams.size(); i++) {
            condSql = condSql.replace("$" + (i + 1), "$" + (columns.size() + i + 1));
        }
        query.append(" WHERE ").append(condSql);
    }

    if (orderBy != null && !orderBy.isEmpty()) {
      query.append(" ORDER BY ")
        .append(orderBy.stream().map(OrderBy::toSQL).collect(Collectors.joining(", ")));
    }

    if (limit != null) {
      query.append(" LIMIT ").append(limit);
    }

    query.append(" RETURNING *");

    return query.toString();
  }*/

  @Override
  public String toSQL() {
    StringBuilder query = new StringBuilder("UPDATE ").append(table).append(" SET ");

    // Generate SET clause with placeholders: $1, $2, ...
    for (int i = 0; i < columns.size(); i++) {
      query.append(columns.get(i)).append(" = $").append(i + 1);
      if (i < columns.size() - 1) {
        query.append(", ");
      }
    }

    // Handle WHERE condition
    if (condition != null) {
      List<Object> conditionParams = new ArrayList<>();
      String rawConditionSql = condition.toSQL(conditionParams); // Generates $1, $2 etc.

      // Shift placeholders by number of columns in SET clause
      int shiftBy = columns.size();
      StringBuffer shiftedSql = new StringBuffer();
      Matcher matcher = Pattern.compile("\\$(\\d+)").matcher(rawConditionSql);

      while (matcher.find()) {
        int originalIndex = Integer.parseInt(matcher.group(1));
        matcher.appendReplacement(shiftedSql, "\\$" + (originalIndex + shiftBy));
      }
      matcher.appendTail(shiftedSql);
      query.append(" WHERE ").append(shiftedSql);
    }

    // Optional ORDER BY
    if (orderBy != null && !orderBy.isEmpty()) {
      query
          .append(" ORDER BY ")
          .append(orderBy.stream().map(OrderBy::toSQL).collect(Collectors.joining(", ")));
    }

    // Optional LIMIT
    if (limit != null) {
      query.append(" LIMIT ").append(limit);
    }

    // Always RETURNING *
    query.append(" RETURNING *");

    return query.toString();
  }

  @Override
  public List<Object> getQueryParams() {
//      List<Object> params = values;
    LOGGER.info("getQueryParams: values: {}" , values);
    List<Object> params = new ArrayList<>(values);  // Make a mutable copy
    if (condition != null) {
      params.addAll(condition.getQueryParams());
    }
    return params;
  }

  public String getTable() {
    return table;
  }

  public UpdateQuery setTable(String table) {
    this.table = table;
    return this;
  }

  public List<String> getColumns() {
    return columns;
  }

  public UpdateQuery setColumns(List<String> columns) {
    this.columns = columns;
    return this;
  }

  public List<Object> getValues() {
    return values;
  }

  public UpdateQuery setValues(List<Object> values) {
    this.values = values;
    return this;
  }

  public Condition getCondition() {
    return condition;
  }

  public UpdateQuery setCondition(Condition condition) {
    this.condition = condition;
    return this;
  }

  public Integer getLimit() {
    return limit;
  }

  public UpdateQuery setLimit(Integer limit) {
    this.limit = limit;
    return this;
  }

  public List<OrderBy> getOrderBy() {
    return orderBy;
  }

  public UpdateQuery setOrderBy(List<OrderBy> orderBy) {
    this.orderBy = orderBy;
    return this;
  }
}