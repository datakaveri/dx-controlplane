package org.cdpg.dx.database.elastic.model;

import io.vertx.core.json.JsonObject;
import java.util.List;

public class SearchCriteriaDTO {
  private String field;
  private String searchType;
  private List<Object> values;

  public SearchCriteriaDTO() {}

  public SearchCriteriaDTO(String field, String searchType, List<Object> values) {
    this.field = field;
    this.searchType = searchType;
    this.values = values;
  }

  // ✅ Lightweight fromJson using Vert.x JsonObject
  public static SearchCriteriaDTO fromJson(JsonObject json) {
    String field = json.getString("field");
    String searchType = json.getString("searchType");
    List<Object> values = json.getJsonArray("values").getList();

    return new SearchCriteriaDTO(field, searchType, values);
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public String getSearchType() {
    return searchType;
  }

  public void setSearchType(String searchType) {
    this.searchType = searchType;
  }

  public List<Object> getValues() {
    return values;
  }

  public void setValues(List<Object> values) {
    this.values = values;
  }
}
