package org.cdpg.dx.database.elastic.model;

import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_APPS;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_DATA_BANK;
import static org.cdpg.dx.database.elastic.util.Constants.*;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxEsException;
import org.cdpg.dx.database.elastic.util.AggregationType;
import org.cdpg.dx.database.elastic.util.QueryType;

public class QueryDecoder {
  private static final Logger LOGGER = LogManager.getLogger(QueryDecoder.class);

  /**
   * Apply default exclusion filters and access policy constraints
   * only for non-asset searches.
   *
   * Rules:
   * - For normal searches:
   *   - Add {@link AccessPolicyQueryDecorator} to enforce access policy rules.
   *   - Exclude DataBank and AiModel items with {@code dataUploadStatus = false}.
   *   - Exclude DataBank, AiModel, and Apps with {@code publishStatus = PENDING}.
   *
   * - For asset searches (searchType matching {@code MY_ASSETS_SEARCH_REGEX} i.e. "myAssetsAll_*"):
   *   - Skip these exclusions to allow owners to view all their assets
   *     regardless of upload or publish status.
   */

  public QueryModel getQueryModel(QueryDecoderRequestDTO request) {

    String searchType = request.getSearchType();
    boolean isValidQuery = false;

    if ("getParentObjectInfo".equalsIgnoreCase(searchType)) {
      LOGGER.info("getParentObjectInfo query");
      return buildGetParentObjectInfoQuery(request);
    }

    Map<FilterType, List<QueryModel>> queryMap = new HashMap<>();
    for (FilterType filterType : FilterType.values()) {
      queryMap.put(filterType, new ArrayList<>());
    }

    if (searchType != null && searchType.matches(SEARCH_CRITERIA_REGEX)) {
      LOGGER.debug("Info: searchCriteria block");
      try {
        new SearchCriteriaQueryDecorator(queryMap, request.getSearchCriteriaRequest()).add();
        isValidQuery = true;
      } catch (DxEsException e) {
        LOGGER.error("SearchCriteriaQueryDecorator failed: {}", e.getMessage());
        throw e;
      } catch (Exception e) {
        LOGGER.error("Unexpected error in SearchCriteriaQueryDecorator: {}", e.getMessage());
        throw new DxEsException("Failed to process searchCriteria block");
      }
    }

    if (searchType != null && searchType.matches(TEXTSEARCH_REGEX)) {
      LOGGER.debug("Info: Text search block");
      try {
        new TextSearchQueryDecorator(queryMap, request.getTextSearchRequest()).add();
        isValidQuery = true;
      } catch (DxEsException e) {
        LOGGER.error("TextSearchQueryDecorator failed: {}", e.getMessage());
        throw e;
      } catch (Exception e) {
        LOGGER.error("Unexpected error in TextSearchQueryDecorator: {}", e.getMessage());
        throw new DxEsException("Failed to process text search block");
      }
    }

    if (!searchType.matches(PF_ASSETS_SEARCH_REGEX)
        && !searchType.matches(ORG_ASSETS_SEARCH_REGEX)) {
      try {
        new AccessPolicyQueryDecorator(queryMap, request.getAccessPolicyRequest()).add();
      } catch (DxEsException e) {
        LOGGER.error("AccessPolicyQueryDecorator failed: {}", e.getMessage());
        throw e;
      } catch (Exception e) {
        LOGGER.error("Unexpected error in AccessPolicyQueryDecorator: {}", e.getMessage());
        throw new DxEsException("Failed to process access policy block");
      }
    }

    // Exclude blocks only if NOT myAssetsAll search
    if (!searchType.matches(MY_ASSETS_SEARCH_REGEX)
        && !searchType.matches(PF_ASSETS_SEARCH_REGEX)
        && !searchType.matches(ORG_ASSETS_SEARCH_REGEX)) {
      QueryModel excludeDatabankFalse = buildUploadStatusExclusion(ITEM_TYPE_DATA_BANK);
      QueryModel excludeAiModelFalse = buildUploadStatusExclusion(ITEM_TYPE_AI_MODEL);
      QueryModel excludePendingApps =
          buildPendingPublishStatusExclusion(List.of(ITEM_TYPE_DATA_BANK, ITEM_TYPE_AI_MODEL,
              ITEM_TYPE_APPS));
      queryMap.get(FilterType.MUST_NOT).add(excludeDatabankFalse);
      queryMap.get(FilterType.MUST_NOT).add(excludeAiModelFalse);
      queryMap.get(FilterType.MUST_NOT).add(excludePendingApps);
    }

    if (searchType.matches(MY_ASSETS_SEARCH_REGEX)) {
      isValidQuery = true;
    }

    if (searchType.matches(PF_ASSETS_SEARCH_REGEX)) {
      isValidQuery = true;
    }

    if (searchType.matches(ORG_ASSETS_SEARCH_REGEX)) {
      LOGGER.debug("Info: Organisation assets search block;");
      isValidQuery = true;

      // Add organisationId filter by default
      QueryModel boolQuery = buildOrganisationFilter(request);
      queryMap.get(FilterType.MUST).add(boolQuery);
    }

    if (searchType.matches(RESPONSE_FILTER_REGEX)) {
      LOGGER.debug("Info: Response filter block");
      try {
        new ResponseFilterDecorator(queryMap, request.getResponseFilterRequest()).add();
        isValidQuery = true;
      } catch (DxEsException e) {
        LOGGER.error("ResponseFilterDecorator failed: {}", e.getMessage());
        throw e;
      } catch (Exception e) {
        LOGGER.error("Unexpected error in ResponseFilterDecorator: {}", e.getMessage());
        throw new DxEsException("Failed to process response filter block");
      }
    }


    if (!isValidQuery) {
      throw new DxEsException("Invalid search query");
    }

    QueryModel q = new QueryModel();
    q.setQueries(getBoolQuery(queryMap));

    //Setting source field
    for (QueryModel qm : queryMap.get(FilterType.INCLUDES)) {
      if (qm.getIncludeFields() != null) {
        q.setIncludeFields(qm.getIncludeFields());
      }
    }

    // Optional pagination support
    if (request.getSize() != null) {
      int size = request.getSize();
      q.setLimit(String.valueOf(size));
      if (request.getPage() != null) {
        int offset = (request.getPage() - 1) * size;
        q.setOffset(String.valueOf(offset));
      }
      return q;
    }
    return q;
  }

  private static QueryModel buildOrganisationFilter(QueryDecoderRequestDTO request) {

    // --- must: organisationId ---
    Map<String, Object> matchParams = new HashMap<>();
    matchParams.put(FIELD, ORGANIZATION_ID_KEYWORD);
    matchParams.put(VALUE, request.getOrganisationId());

    QueryModel orgMatchQuery = new QueryModel(QueryType.MATCH, matchParams);

    QueryModel boolQuery = new QueryModel(QueryType.BOOL);
    List<QueryModel> mustQueries = new ArrayList<>();
    mustQueries.add(orgMatchQuery);
    boolQuery.setMustQueries(mustQueries);

    // --- must_not: ownerId == sub (only when filter_myassets=true) ---
    if (request.isFilterMyAssets()) {
      String sub = request.getAccessPolicyRequest().getSub();

      if (sub != null && !sub.isBlank()) {
        Map<String, Object> excludeOwnerParams = new HashMap<>();
        excludeOwnerParams.put(FIELD, OWNER_USER_ID_KEYWORD);
        excludeOwnerParams.put(VALUE, sub);

        QueryModel excludeOwnerQuery =
            new QueryModel(QueryType.MATCH, excludeOwnerParams);

        List<QueryModel> mustNotQueries = new ArrayList<>();
        mustNotQueries.add(excludeOwnerQuery);
        boolQuery.setMustNotQueries(mustNotQueries);
      }
    }

    return boolQuery;
  }

  public QueryModel getOrganisationAssetsQuery(QueryDecoderRequestDTO request) {
    LOGGER.debug("getOrganisationAssetsQuery - {}", request);

    // Add organisationId filter by default
    QueryModel boolQuery = buildOrganisationFilter(request);

    // Add publishStatus filter if present in request
    if (request.getPublishStatus() != null && !request.getPublishStatus().isEmpty()) {
      Map<String, Object> statusParams = new HashMap<>();
      statusParams.put(FIELD, "publishStatus.keyword");
      statusParams.put(VALUE, request.getPublishStatus());

      QueryModel statusQuery = new QueryModel(QueryType.MATCH, statusParams);
      boolQuery.addMustQuery(statusQuery);
    }

    QueryModel q = new QueryModel();
    q.setQueries(boolQuery);

    // Pagination support
    if (request.getSize() != null) {
      int size = request.getSize();
      q.setLimit(String.valueOf(size));
      if (request.getPage() != null) {
        int offset = (request.getPage() - 1) * size;
        q.setOffset(String.valueOf(offset));
      }
    }

    return q;
  }

  public QueryModel getPlatformAssetsQuery(QueryDecoderRequestDTO request) {
    LOGGER.debug("getPlatformAssetsQuery - {}", request);

    Map<String, Object> termsParams = new HashMap<>();
    termsParams.put(FIELD, TYPE_KEYWORD);
    termsParams.put(VALUE, List.of(ITEM_TYPE_DATA_BANK, ITEM_TYPE_AI_MODEL, ITEM_TYPE_APPS));

    QueryModel termsQuery = new QueryModel(QueryType.TERMS, termsParams);

    // Create the bool query with the match query in the must clause
    QueryModel boolQuery = new QueryModel();
    boolQuery.setQueryType(QueryType.BOOL);
    List<QueryModel> mustQueries = new ArrayList<>();
    mustQueries.add(termsQuery);

    // Add publishStatus filter if present in request
    if (request.getPublishStatus() != null && !request.getPublishStatus().isEmpty()) {
      Map<String, Object> statusParams = new HashMap<>();
      statusParams.put(FIELD, "publishStatus.keyword");
      statusParams.put(VALUE, request.getPublishStatus());

      QueryModel statusQuery = new QueryModel(QueryType.MATCH, statusParams);
      mustQueries.add(statusQuery);
    }

    boolQuery.setMustQueries(mustQueries);

    QueryModel q = new QueryModel();
    q.setQueries(boolQuery);

    // Pagination support
    if (request.getSize() != null) {
      int size = request.getSize();
      q.setLimit(String.valueOf(size));
      if (request.getPage() != null) {
        int offset = (request.getPage() - 1) * size;
        q.setOffset(String.valueOf(offset));
      }
    }

    return q;
  }

  public QueryModel listMultipleItemTypesQuery(QueryDecoderRequestDTO request) {
    LOGGER.debug("listMultipleItemTypesQuery - {}", request);

    Map<FilterType, List<QueryModel>> queryMap = new HashMap<>();
    for (FilterType filterType : FilterType.values()) {
      queryMap.put(filterType, new ArrayList<>());
    }

    try {
      new AccessPolicyQueryDecorator(queryMap, request.getAccessPolicyRequest()).add();
      new SearchCriteriaQueryDecorator(queryMap, request.getSearchCriteriaRequest()).add();
      new InstanceFilterQueryDecorator(queryMap, request.getInstanceFilterRequest()).add();
    } catch (DxEsException ex) {
      LOGGER.error("Failed while creating ES query: {}", ex.getMessage(), ex);
      throw new DxBadRequestException(ex.getMessage(), ex);
    }

    QueryModel excludeDatabankFalse = buildUploadStatusExclusion(ITEM_TYPE_DATA_BANK);
    QueryModel excludeAiModelFalse = buildUploadStatusExclusion(ITEM_TYPE_AI_MODEL);
    QueryModel excludePendingApps =
        buildPendingPublishStatusExclusion(List.of(ITEM_TYPE_DATA_BANK, ITEM_TYPE_AI_MODEL,
            ITEM_TYPE_APPS));
    queryMap.get(FilterType.MUST_NOT).add(excludeDatabankFalse);
    queryMap.get(FilterType.MUST_NOT).add(excludeAiModelFalse);
    queryMap.get(FilterType.MUST_NOT).add(excludePendingApps);

    QueryModel finalQuery = new QueryModel();
    finalQuery.setQueries(getBoolQuery(queryMap));

    List<String> filters = request.getFilter();
    int size =
        request.getSize() != null
            ? request.getSize()
            : FILTER_PAGINATION_SIZE - (request.getPage() != null ? request.getPage() : 1);
    List<QueryModel> aggs = new ArrayList<>();

    if (filters != null) {
      for (String filter : filters) {
        String aggField = filter.equals(RESOURCE_SVR_URL) ? filter : filter + KEYWORD_KEY;
        Map<String, Object> aggParams = Map.of(FIELD, aggField, SIZE_KEY, size);

        QueryModel agg = new QueryModel();
        agg.setAggregationType(AggregationType.TERMS);
        agg.setAggregationName(filter);
        agg.setAggregationParameters(aggParams);
        aggs.add(agg);
      }
      finalQuery.setAggregations(aggs);
    }

    if (request.getPage() != null) {
      finalQuery.setLimit(String.valueOf(size));
    }

    return finalQuery;
  }

  private QueryModel buildUploadStatusExclusion(String itemType) {
    return new QueryModel(QueryType.BOOL)
        .setMustQueries(
            List.of(
                new QueryModel(QueryType.TERM)
                    .setQueryParameters(
                        Map.of(
                            FIELD, TYPE_KEYWORD,
                            VALUE, itemType)),
                new QueryModel(QueryType.TERM)
                    .setQueryParameters(Map.of(FIELD, DATA_UPLOAD_STATUS, VALUE, false))));
  }

  private QueryModel buildPendingPublishStatusExclusion(List<String> itemTypes) {
    return new QueryModel(QueryType.BOOL)
        .setMustQueries(
            List.of(
                new QueryModel(QueryType.TERMS)
                    .setQueryParameters(
                        Map.of(
                            FIELD, TYPE_KEYWORD,
                            VALUE, itemTypes)),
                new QueryModel(QueryType.TERM)
                    .setQueryParameters(Map.of(FIELD, PUBLISH_STATUS + KEYWORD_KEY, VALUE,
                        PENDING))));
  }

  private QueryModel buildGetParentObjectInfoQuery(QueryDecoderRequestDTO request) {
    String id = request.getId();
    String[] fields = {
      "type",
      "provider",
      "ownerUserId",
      "resourceGroup",
      "name",
      "organizationId",
      "shortDescription",
      "resourceServer",
      "resourceServerRegURL",
      "cos",
      "cos_admin"
    };
    List<QueryModel> mustQueries =
        List.of(
            new QueryModel(QueryType.TERM)
                .setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id)));
    QueryModel boolQuery = new QueryModel(QueryType.BOOL);
    boolQuery.setMustQueries(mustQueries);
    boolQuery.setIncludeFields(Arrays.asList(fields));
    return boolQuery;
  }

  public QueryModel buildGetItemWithNameExistsQuery(String type, String name) {
    QueryModel typeMatchQuery = new QueryModel(QueryType.MATCH);
    typeMatchQuery.setQueryParameters(Map.of(FIELD, TYPE_KEY, VALUE, type));
    QueryModel nameMatchQuery = new QueryModel(QueryType.MATCH);
    nameMatchQuery.setQueryParameters(Map.of(FIELD, NAME + KEYWORD_KEY, VALUE, name));

    QueryModel boolQuery = new QueryModel(QueryType.BOOL);
    boolQuery.setMustQueries(List.of(typeMatchQuery, nameMatchQuery));
    return boolQuery;
  }

  public QueryModel getItemIdOrgIdQueryModel(String id, String orgId) {
        List<QueryModel> filterQueries = buildIdTermQuery(id);

        if (!orgId.isBlank()) {
          LOGGER.debug("getItemIdOrgIdQueryModel - orgId: {}", orgId);
            QueryModel ordIdTermQuery = new QueryModel(QueryType.TERM);
            ordIdTermQuery.setQueryParameters(Map.of(FIELD, ORGANIZATION_ID_KEYWORD, VALUE, orgId));
            filterQueries.add(ordIdTermQuery);
        }

        return buildBooleanQuery(filterQueries);
    }

  public QueryModel getItemIdOwnerIdQueryModel(String itemId, String ownerId) {
    List<QueryModel> filterQueries = buildIdTermQuery(itemId);

    if (ownerId != null && !ownerId.isBlank()) {
      LOGGER.debug("getItemIdOwnerIdQueryModel - ownerId: {}", ownerId);
      QueryModel ownerIdTermQuery = new QueryModel(QueryType.TERM);
      ownerIdTermQuery.setQueryParameters(Map.of(FIELD, PROVIDER_USER_ID + KEYWORD_KEY, VALUE, ownerId));
      filterQueries.add(ownerIdTermQuery);
    }

    return buildBooleanQuery(filterQueries);
  }


  public QueryModel getItemIdQueryModel(String id) {
        return buildBooleanQuery(buildIdTermQuery(id));
    }

    private List<QueryModel> buildIdTermQuery(String id) {
        QueryModel idTermQuery = new QueryModel(QueryType.TERM);
        idTermQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));
        List<QueryModel> filterQueries = new ArrayList<>();
        filterQueries.add(idTermQuery);
        return filterQueries;
    }

    private QueryModel buildBooleanQuery(List<QueryModel> filterQueries) {
        QueryModel boolQuery = new QueryModel(QueryType.BOOL);
        boolQuery.setMustQueries(filterQueries);

        QueryModel query = new QueryModel();
        query.setQueries(boolQuery);
        return query;
    }

  private QueryModel getBoolQuery(Map<FilterType, List<QueryModel>> filterQueries) {
    QueryModel boolQuery = new QueryModel(QueryType.BOOL);
    for (Map.Entry<FilterType, List<QueryModel>> entry : filterQueries.entrySet()) {
      switch (entry.getKey()) {
        case MUST -> boolQuery.setMustQueries(entry.getValue());
        case FILTER -> boolQuery.setFilterQueries(entry.getValue());
        case MUST_NOT -> boolQuery.setMustNotQueries(entry.getValue());
        case SHOULD -> boolQuery.setShouldQueries(entry.getValue());
      }
    }
    return boolQuery;
  }

  public QueryModel ownerShipTransferQuery(String oldOwnerId, String newOwnerId,String organizationId) {
    QueryModel updateByQueryModel = new QueryModel();

    QueryModel boolQuery = new QueryModel();
    boolQuery.setQueryType(QueryType.BOOL);

    List<QueryModel> mustQueries = new ArrayList<>();

    QueryModel ownerUserIdQuery = new QueryModel();
    ownerUserIdQuery.setQueryType(QueryType.TERM);
    ownerUserIdQuery.setQueryParameters(Map.of(FIELD, OWNER_USER_ID_KEYWORD,VALUE, oldOwnerId));

    QueryModel orgIdQuery = new QueryModel();
    orgIdQuery.setQueryType(QueryType.TERM);
    orgIdQuery.setQueryParameters(Map.of(FIELD, ORGANIZATION_ID_KEYWORD,VALUE, organizationId));

    mustQueries.add(ownerUserIdQuery);
    mustQueries.add(orgIdQuery);

    boolQuery.setMustQueries(mustQueries);

    boolQuery.setScriptSource("ctx._source.ownerUserId = params.newOwner");
    boolQuery.setScriptLanguage("painless");

    Map<String, Object> scriptParams = new HashMap<>();
    scriptParams.put("newOwner", newOwnerId);
    boolQuery.setScriptParams(scriptParams);

    updateByQueryModel.setQueries(boolQuery);

    return updateByQueryModel;

  }

  public QueryModel setCountAggregations() {
    QueryModel agg = new QueryModel();
    agg.setAggregationType(AggregationType.TERMS);
    agg.setAggregationName(RESULTS);
    Map<String, Object> aggParams = Map.of(FIELD, TYPE_KEYWORD);
    agg.setAggregationParameters(aggParams);
    return agg;
  }
}
