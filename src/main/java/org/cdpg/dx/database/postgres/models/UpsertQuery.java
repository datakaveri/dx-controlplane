package org.cdpg.dx.database.postgres.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@DataObject(generateConverter = true)
public class UpsertQuery implements Query {

  private static final Logger LOGGER = LogManager.getLogger(UpsertQuery.class);

  private String table;
  private List<String> columns;
  private List<Object> values;
  private List<String> conflictColumns;
  private List<String> updateColumns;

  // Default constructor
  public UpsertQuery() {}

  // Full constructor
  public UpsertQuery(
      String table,
      List<String> columns,
      List<Object> values,
      List<String> conflictColumns,
      List<String> updateColumns) {
    this.table = Objects.requireNonNull(table, "Table cannot be null");
    this.columns = Objects.requireNonNull(columns, "Columns cannot be null");
    this.values = Objects.requireNonNull(values, "Values cannot be null");
    this.conflictColumns = conflictColumns;
    this.updateColumns = updateColumns;
  }

  // JSON constructor
  public UpsertQuery(JsonObject json) {
    UpsertQueryConverter.fromJson(json, this);
  }

  // Convert to JSON
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    UpsertQueryConverter.toJson(this, json);
    return json;
  }

  // Getters and setters
  public String getTable() {
    return table;
  }

  public UpsertQuery setTable(String table) {
    this.table = table;
    return this;
  }

  public List<String> getColumns() {
    return columns;
  }

  public UpsertQuery setColumns(List<String> columns) {
    this.columns = columns;
    return this;
  }

  public List<Object> getValues() {
    return values;
  }

  public UpsertQuery setValues(List<Object> values) {
    this.values = values;
    return this;
  }

  public List<String> getConflictColumns() {
    return conflictColumns;
  }

  public UpsertQuery setConflictColumns(List<String> conflictColumns) {
    this.conflictColumns = conflictColumns;
    return this;
  }

  public List<String> getUpdateColumns() {
    return updateColumns;
  }

  public UpsertQuery setUpdateColumns(List<String> updateColumns) {
    this.updateColumns = updateColumns;
    return this;
  }

  // Generate SQL string
  @Override
  public String toSQL() {

    String placeholders =
        IntStream.rangeClosed(1, columns.size())
            .mapToObj(i -> "$" + i)
            .collect(Collectors.joining(", "));

    String conflictAction;

    if (updateColumns == null || updateColumns.isEmpty()) {
      conflictAction = "DO NOTHING";
    } else {
      String updates =
          updateColumns.stream()
              .map(col -> col + " = EXCLUDED." + col)
              .collect(Collectors.joining(", "));
      conflictAction = "DO UPDATE SET " + updates;
    }

    return String.format(
        "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (%s) %s RETURNING *",
        table,
        String.join(", ", columns),
        placeholders,
        String.join(", ", conflictColumns),
        conflictAction);
  }

  @Override
  public List<Object> getQueryParams() {
    return new ArrayList<>(values);
  }

  @Override
  public String toString() {
    return "UpsertQuery{"
        + "table='"
        + table
        + '\''
        + ", columns="
        + columns
        + ", values="
        + values
        + ", conflictColumns="
        + conflictColumns
        + ", updateColumns="
        + updateColumns
        + '}';
  }
}
