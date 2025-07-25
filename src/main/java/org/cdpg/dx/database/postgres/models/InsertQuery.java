package org.cdpg.dx.database.postgres.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@DataObject(generateConverter = true)
public class InsertQuery implements Query {
    private static final Logger LOGGER = LogManager.getLogger(InsertQuery.class);
    private String table;
    private List<String> columns;
    private List<Object> values;

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
        InsertQueryConverter.fromJson(json, this);  // Use generated converter
    }

    // Convert to JSON
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        InsertQueryConverter.toJson(this, json);
        return json;
    }

    // Getters & Setters (Required for DataObject)
    public String getTable() { return table; }
    public InsertQuery setTable(String table) { this.table = table;
        return this;
    }

    public List<String> getColumns() { return columns; }
    public InsertQuery setColumns(List<String> columns) { this.columns = columns;
        return this;
    }

    public List<Object> getValues() { return values; }
    public InsertQuery setValues(List<Object> values) { this.values = values;
        return this;
    }


    @Override
    public String toSQL() {
        String placeholders =
            java.util.stream.IntStream.range(0, columns.size())
                .mapToObj(i -> "$"+(i+1))
                .collect(Collectors.joining(", "));

        String finalQuery = "INSERT INTO " + table +
            " (" + String.join(", ", columns) + ") " +
            "VALUES (" + placeholders + ") RETURNING *"; // ADD THIS
        LOGGER.info("Final Query: " + finalQuery);
        return finalQuery;
    }


    @Override
    public List<Object> getQueryParams() {
        return values;
    }

    @Override
    public String toString() {
        return "InsertQuery{" +
            "table='" + table + '\'' +
            ", columns=" + columns +
            ", values=" + values +
            '}';
    }
}
