package org.cdpg.dx.aaa.common;

import static org.cdpg.dx.aaa.common.Constants.RESULTS;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;

public class ResponseModel {
  JsonObject response;
  private List<JsonObject> elasticsearchResponses;
  private int totalHits;
  private PaginationInfo paginationInfo;

  public ResponseModel(
      List<ElasticsearchResponse> elasticsearchResponses, int size, int page, int totalHits) {
    this.response = new JsonObject();
    this.response.put(RESULTS, elasticsearchResponses);
    setTotalHits(totalHits);
    this.elasticsearchResponses =
        getJsonObjectList(Objects.requireNonNullElse(elasticsearchResponses, List.of()));
    setPaginationInfo(page, size);
    setResponseJson();
  }

  public ResponseModel(
      List<ElasticsearchResponse> elasticsearchResponses, JsonObject aggregations) {
    this.elasticsearchResponses =
        getJsonObjectList(Objects.requireNonNullElse(elasticsearchResponses, List.of()));
    this.response = new JsonObject();
    this.response.put(RESULTS, setAggregationsList(aggregations));
  }

  private JsonArray setAggregationsList(JsonObject aggregations) {
    JsonArray results = new JsonArray();
    results.add(aggregations);
    return results;
  }

  public List<JsonObject> getElasticsearchResponses() {
    return elasticsearchResponses;
  }

  private void setPaginationInfo(int page, int size) {
    int totalPages = (int) Math.ceil((double) this.totalHits / size);
    boolean hasNext = page < totalPages;
    boolean hasPrevious = page > 1;
    this.paginationInfo =
        new PaginationInfo(page, size, this.totalHits, totalPages, hasNext, hasPrevious);
  }

  private List<JsonObject> getJsonObjectList(List<ElasticsearchResponse> elasticsearchResponses) {
    List<JsonObject> jsonObjectList = new ArrayList<>();
    for (ElasticsearchResponse elasticsearchResponse : elasticsearchResponses) {
      jsonObjectList.add(elasticsearchResponse.getSource());
    }
    return jsonObjectList;
  }

  public JsonObject getResponse() {
    return response;
  }

  public void setResponseJson() {
    response = new JsonObject().put(RESULTS, elasticsearchResponses);
  }

  public int getTotalHits() {
    return totalHits;
  }

  public void setTotalHits(int totalHits) {
    this.totalHits = totalHits;
  }

  @Override
  public String toString() {
    return "ResponseModel{" + "elasticsearchResponses=" + elasticsearchResponses + '}';
  }

  public PaginationInfo getPaginationInfo() {
    return paginationInfo;
  }
}
