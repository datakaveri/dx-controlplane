package org.cdpg.dx.database.postgres.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DataObject(generateConverter = true)
@JsonGen(inheritConverter = true, publicConverter = false)
public class DeleteQuery implements Query {
    private static final Logger LOG = LoggerFactory.getLogger(DeleteQuery.class);
    private  String table;
    private  Condition condition;
    private  List<OrderBy> orderBy;
    private  Integer limit; // Optional, so keep as Integer

    private List<Object> queryParams = new ArrayList<>();

    public DeleteQuery(){}
    // Constructor (OrderBy & Limit are optional)
    public DeleteQuery(String table, Condition condition, List<OrderBy> orderBy, Integer limit) {
        this.table = Objects.requireNonNull(table, "Table name cannot be null");
        this.condition = condition;
        this.orderBy = orderBy != null ? List.copyOf(orderBy) : List.of();
        this.limit = limit; // Can be null (optional)
    }

    public DeleteQuery(DeleteQuery other){
        this.condition = other.getCondition();
        this.table = other.getTable();
        this.orderBy = other.getOrderBy();
        this.limit = other.getLimit();

    }

    // JSON Constructor
    public DeleteQuery(JsonObject json) {
        DeleteQueryConverter.fromJson(json, this);
    }

    // Convert to JSON
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        DeleteQueryConverter.toJson(this, json);
        return json;
    }

    public String getTable() { return table; }

    public DeleteQuery setTable(String table) {
        this.table = table;
        return this;
    }

    public Condition getCondition() {
        return condition;
    }

    public DeleteQuery setCondition(Condition condition) {
        this.condition = condition;
        return this;
    }

    public List<OrderBy> getOrderBy() { return orderBy; }

    public DeleteQuery setOrderBy(List<OrderBy> orderBy) {
        this.orderBy = orderBy;
        return this;
    }

    public Integer getLimit() { return limit; }

    public DeleteQuery setLimit(Integer limit) {
        this.limit = limit;
        return this;
    }

    @Override
    public String toSQL() {
        queryParams.clear();
        StringBuilder query = new StringBuilder("DELETE FROM " + table + " WHERE " + getCondition().toSQL(queryParams));

        if (!orderBy.isEmpty()) {
            query.append(" ORDER BY ").append(orderBy.stream()
                .map(OrderBy::toSQL)
                .collect(Collectors.joining(", ")));
        }


        if (limit != null) {
            query.append(" LIMIT ").append(limit);
        }

        return query.toString();
    }

    @Override
    public List<Object> getQueryParams() {
        List<Object> params = new ArrayList<>();
        if (condition != null) {
            params.addAll(condition.getQueryParams());
        }
        return params;
    }
}