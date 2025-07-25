package org.cdpg.dx.database.elastic.model;

import java.util.List;

public class ResponseFilterRequestDTO {
  private String searchType;
  private Boolean search;
  private List<String> attribute;
  private List<String> filter;

  public ResponseFilterRequestDTO(
      String searchType, Boolean search, List<String> attribute, List<String> filter) {
    this.searchType = searchType;
    this.search = search;
    this.attribute = attribute;
    this.filter = filter;
  }

  public String getSearchType() {
    return searchType;
  }

  public void setSearchType(String searchType) {
    this.searchType = searchType;
  }

  public Boolean getSearch() {
    return search;
  }

  public void setSearch(Boolean search) {
    this.search = search;
  }

  public List<String> getAttribute() {
    return attribute;
  }

  public void setAttribute(List<String> attribute) {
    this.attribute = attribute;
  }

  public List<String> getFilter() {
    return filter;
  }

  public void setFilter(List<String> filter) {
    this.filter = filter;
  }
}
