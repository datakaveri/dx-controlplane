package org.cdpg.dx.database.elastic.model;

import static org.cdpg.dx.database.elastic.util.Constants.*;

import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.elastic.util.QueryType;

public class InstanceFilterQueryDecorator implements ElasticsearchQueryDecorator {
  private static final Logger LOGGER = LogManager.getLogger(InstanceFilterQueryDecorator.class);
  private final Map<FilterType, List<QueryModel>> queryMap;
  private final InstanceFilterRequestDTO request;

  public InstanceFilterQueryDecorator(
      Map<FilterType, List<QueryModel>> queryMap, InstanceFilterRequestDTO request) {
    this.queryMap = queryMap;
    this.request = request;
  }

  @Override
  public Map<FilterType, List<QueryModel>> add() {
    if (request.getInstance() != null && !request.getInstance().isEmpty()) {
      String instanceId = request.getInstance();
      LOGGER.info("Adding instance filter query decorator {}", instanceId);
      QueryModel instanceFilter =
          new QueryModel(QueryType.TERM)
              .setQueryParameters(Map.of(FIELD, INSTANCE + KEYWORD_KEY, VALUE, instanceId));
      queryMap.get(FilterType.FILTER).add(instanceFilter);
    }
    return queryMap;
  }
}
