package org.cdpg.dx.database.elastic.model;

import static org.cdpg.dx.aaa.common.Constants.PII;
import static org.cdpg.dx.database.elastic.util.Constants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.elastic.util.*;

public class AccessPolicyQueryDecorator implements ElasticsearchQueryDecorator {
  private static final Logger LOGGER = LogManager.getLogger(AccessPolicyQueryDecorator.class);
  private final Map<FilterType, List<QueryModel>> queryMap;
  private final AccessPolicyRequestDTO request;

  public AccessPolicyQueryDecorator(
      Map<FilterType, List<QueryModel>> queryMap, AccessPolicyRequestDTO request) {
    this.queryMap = queryMap;
    this.request = request;
  }

  @Override
  public Map<FilterType, List<QueryModel>> add() {
    LOGGER.info("Adding access policy query decorator DTO {}", request);
    String sub = request != null ? request.getSub() : null;
    boolean isMyAssetsRequest = request != null && Boolean.TRUE.equals(request.getMyAssetsReq());

    if (sub != null && !sub.isEmpty()) {
      if (isMyAssetsRequest) {
        // Strictly match owned items only
        QueryModel ownerMatch =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, PROVIDER_USER_ID, VALUE, sub));
        queryMap.get(FilterType.MUST).add(ownerMatch);
      } else {
        // User is authenticated → allow: PUBLIC, RESTRICTED, PRIVATE owned
        QueryModel publicAccess =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, OPEN));
        QueryModel restrictedAccess =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, RESTRICTED));
        QueryModel piiAccess =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, PII));
        QueryModel privateAccess =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, PRIVATE));
        QueryModel ownerMatch =
            new QueryModel(QueryType.MATCH)
                .setQueryParameters(Map.of(FIELD, PROVIDER_USER_ID, VALUE, sub));
        QueryModel privateOwned =
            new QueryModel(QueryType.BOOL).setMustQueries(List.of(privateAccess, ownerMatch));

        List<QueryModel> shouldQueries =
            new ArrayList<>(List.of(
                publicAccess,
                restrictedAccess,
                privateOwned,
                piiAccess));
        List<String> sharedItemIds = request.getSharedItemIds();

        if (sharedItemIds != null && !sharedItemIds.isEmpty()) {

          QueryModel sharedItemsQuery =
              new QueryModel(QueryType.TERMS)
                  .setQueryParameters(
                      Map.of(
                          FIELD, ID_KEYWORD,
                          VALUE, sharedItemIds));

          shouldQueries.add(sharedItemsQuery);
        }

        QueryModel accessFilter = new QueryModel(QueryType.BOOL);
        accessFilter.setShouldQueries(shouldQueries);
        accessFilter.setMinimumShouldMatch("1");

        queryMap.get(FilterType.MUST).add(accessFilter);
      }
    } else {
      QueryModel excludePrivate =
          new QueryModel(QueryType.MATCH)
              .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, PRIVATE));
      queryMap.get(FilterType.MUST_NOT).add(excludePrivate);
    }
    return queryMap;
  }
}
