package org.cdpg.dx.database.elastic.model;

import java.util.List;

public class SearchCriteriaRequestDTO {
  private List<SearchCriteriaDTO> searchCriteria;
  private List<String> filter;

  public SearchCriteriaRequestDTO(List<SearchCriteriaDTO> searchCriteria, List<String> filter) {
    this.searchCriteria = searchCriteria;
    this.filter = filter;
  }

  public List<SearchCriteriaDTO> getSearchCriteria() {
    return searchCriteria;
  }

  public void setSearchCriteria(List<SearchCriteriaDTO> searchCriteria) {
    this.searchCriteria = searchCriteria;
  }

  public List<String> getFilter() {
    return filter;
  }

  public void setFilter(List<String> filter) {
    this.filter = filter;
  }
}
