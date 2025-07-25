package org.cdpg.dx.database.elastic.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class AggregationResponse {
  private JsonArray results;

  public AggregationResponse(JsonObject json) {
    AggregationResponseConverter.fromJson(json, this);
  }

  public AggregationResponse(JsonArray results) {
    this.results = results;
  }

  public JsonArray getResults() {
    return results;
  }

  public void setResults(JsonArray results) {
    this.results = results;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    AggregationResponseConverter.toJson(this, json);
    return json;
  }
}
