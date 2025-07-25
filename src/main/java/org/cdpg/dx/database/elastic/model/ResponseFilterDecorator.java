package org.cdpg.dx.database.elastic.model;

import static org.cdpg.dx.database.elastic.util.Constants.*;

import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxEsException;
import org.cdpg.dx.database.elastic.util.QueryType;

public class ResponseFilterDecorator implements ElasticsearchQueryDecorator {
  private static final Logger LOGGER = LogManager.getLogger(ResponseFilterDecorator.class);
  private final Map<FilterType, List<QueryModel>> queryMap;
  private final ResponseFilterRequestDTO request;

  public ResponseFilterDecorator(
      Map<FilterType, List<QueryModel>> queryMap, ResponseFilterRequestDTO request) {
    this.queryMap = queryMap;
    this.request = request;
  }

  @Override
  public Map<FilterType, List<QueryModel>> add() {
    LOGGER.info("Adding response filter query decorator DTO {}", request);
    String searchType = request.getSearchType();
    if (searchType == null || !searchType.matches(RESPONSE_FILTER_REGEX)) {
      return queryMap;
    }
    if (!Boolean.FALSE.equals(request.getSearch())) {
      throw new DxEsException("Operation not allowed: 'search' must be true for filtering");
    }
    List<String> sourceFilter = request.getAttribute();
    if (sourceFilter == null || sourceFilter.isEmpty()) {
      sourceFilter = request.getFilter();
    }
    if (sourceFilter == null || sourceFilter.isEmpty()) {
      throw new DxEsException("Missing response filter: 'attribute' or 'filter' is required");
    }
    QueryModel sourceConfigModel = new QueryModel(QueryType.BOOL);
    sourceConfigModel.setIncludeFields(sourceFilter);
    queryMap.get(FilterType.FILTER).add(sourceConfigModel);
    return queryMap;
  }
}
