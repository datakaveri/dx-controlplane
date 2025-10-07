package org.cdpg.dx.database.postgres.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@DataObject(generateConverter = true)
public class InsertQuery implements Query {
  private static final Logger LOGGER = LogManager.getLogger(InsertQuery.class);
  private String table;
  private List<String> columns;
  private List<Object> values;
  private List<String> conflictColumns;  // columns to check for conflict (e.g., _id)
  private List<String> updateColumns;    // columns to update on conflict

  // Default constructor (Needed for deserialization)
  public InsertQuery() {}

  // Constructor
  public InsertQuery(String table, List<String> columns, List<Object> values) {
    this.table = table;
    this.columns = columns;
    this.values = values;
  }

  // JSON Constructor
  public InsertQuery(JsonObject json) {
    InsertQueryConverter.fromJson(json, this); // Use generated converter
  }

  // Convert to JSON
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    InsertQueryConverter.toJson(this, json);
    return json;
  }

  // Getters & Setters (Required for DataObject)
  public String getTable() {
    return table;
  }

  public InsertQuery setTable(String table) {
    this.table = table;
    return this;
  }

  public List<String> getColumns() {
    return columns;
  }

  public InsertQuery setColumns(List<String> columns) {
    this.columns = columns;
    return this;
  }

  public List<Object> getValues() {
    return values;
  }

  public InsertQuery setValues(List<Object> values) {
    this.values = values;
    return this;
  }

  public List<String> getConflictColumns() {
    return conflictColumns;
  }

  public InsertQuery setConflictColumns(List<String> conflictColumns) {
    this.conflictColumns = conflictColumns;
    return this;
  }

  public List<String> getUpdateColumns() {
    return updateColumns;
  }

  public InsertQuery setUpdateColumns(List<String> updateColumns) {
    this.updateColumns = updateColumns;
    return this;
  }

  @Override
  public String toSQL() {
    String placeholders = IntStream.rangeClosed(1, columns.size())
        .mapToObj(i -> "$" + i)
        .collect(Collectors.joining(", "));

    StringBuilder sql = new StringBuilder();
    sql.append(String.format(
        "INSERT INTO %s (%s) VALUES (%s)",
        table, String.join(", ", columns), placeholders));

    // Add ON CONFLICT clause if conflictColumns and updateColumns are set
    if (conflictColumns != null && !conflictColumns.isEmpty() &&
        updateColumns != null && !updateColumns.isEmpty()) {

      String conflictCols = String.join(", ", conflictColumns);
      String updates = updateColumns.stream()
          .map(col -> col + " = EXCLUDED." + col)
          .collect(Collectors.joining(", "));
      sql.append(" ON CONFLICT (").append(conflictCols).append(") DO UPDATE SET ")
          .append(updates);
    }

    sql.append(" RETURNING *");  // always return inserted/updated row
    return sql.toString();
  }

  @Override
  public List<Object> getQueryParams() {
    return values;
  }

  @Override
  public String toString() {
    return "InsertQuery{"
        + "table='"
        + table
        + '\''
        + ", columns="
        + columns
        + ", values="
        + values
        + '}';
  }
}
