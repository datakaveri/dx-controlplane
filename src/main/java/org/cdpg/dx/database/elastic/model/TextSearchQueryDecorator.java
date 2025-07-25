package org.cdpg.dx.database.elastic.model;

import static org.cdpg.dx.database.elastic.util.Constants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.cdpg.dx.common.exception.DxEsException;
import org.cdpg.dx.database.elastic.util.QueryType;

public class TextSearchQueryDecorator implements ElasticsearchQueryDecorator {
  private final Map<FilterType, List<QueryModel>> queryMap;
  private final TextSearchRequestDTO request;

  public TextSearchQueryDecorator(
      Map<FilterType, List<QueryModel>> queryMap, TextSearchRequestDTO request) {
    this.queryMap = queryMap;
    this.request = request;
  }

  @Override
  public Map<FilterType, List<QueryModel>> add() {
    if (request.q() == null || request.q().isBlank()) {
      throw new DxEsException("bad text query values");
    }
    String textAttr = request.q();
    boolean isFuzzy = Boolean.TRUE.equals(request.fuzzy());
    boolean isAutoComplete = Boolean.TRUE.equals(request.fuzzy());

    List<QueryModel> shouldQueries = new ArrayList<>();

    if (isFuzzy) {
      shouldQueries.add(
          new QueryModel(QueryType.MULTI_MATCH)
              .setQueryParameters(
                  Map.of(
                      "fields",
                      List.of("label", "tags", "description"),
                      "query",
                      textAttr,
                      "fuzziness",
                      "AUTO",
                      "boost",
                      "1.0")));
    }

    if (isAutoComplete) {
      shouldQueries.add(
          new QueryModel(QueryType.MULTI_MATCH)
              .setQueryParameters(
                  Map.of(
                      "fields",
                      List.of("label", "tags", "description"),
                      "query",
                      textAttr,
                      "type",
                      "BoolPrefix",
                      "boost",
                      "5.0")));
    }

    if (!isFuzzy && !isAutoComplete) {
      shouldQueries.add(
          new QueryModel(QueryType.TEXT).setQueryParameters(Map.of(Q_VALUE, textAttr)));
    }

    QueryModel boolModel = new QueryModel(QueryType.BOOL);
    boolModel.setShouldQueries(shouldQueries);
    boolModel.setMinimumShouldMatch("1");

    queryMap.computeIfAbsent(FilterType.MUST, k -> new ArrayList<>()).add(boolModel);
    return queryMap;
  }
}
