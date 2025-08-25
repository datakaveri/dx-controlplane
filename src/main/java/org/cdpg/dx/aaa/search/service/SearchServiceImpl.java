package org.cdpg.dx.aaa.search.service;

import static org.cdpg.dx.database.elastic.util.Constants.COUNT_AGGREGATION_ONLY;
import static org.cdpg.dx.database.elastic.util.Constants.SOURCE_ONLY;

import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.search.util.ResponseModel;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.model.OrderBy;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class SearchServiceImpl implements SearchService {
  private static final Logger LOGGER = LogManager.getLogger(SearchServiceImpl.class);

  private final ElasticsearchService elasticsearchService;
  private final QueryDecoder queryDecoder;
  private final String docIndex;

  public SearchServiceImpl(ElasticsearchService elasticsearchService, String docIndex) {
    this.elasticsearchService = elasticsearchService;
    this.queryDecoder = new QueryDecoder();
    this.docIndex = docIndex;
  }

  @Override
  public Future<ResponseModel> postSearch(QueryDecoderRequestDTO requestDTO) {
    try {
      QueryModel queryModel = buildQueryModel(requestDTO);
      applySorting(queryModel, requestDTO);

      return elasticsearchService.search(docIndex, queryModel, SOURCE_ONLY)
              .map(results -> new ResponseModel(results, requestDTO.getSize(), requestDTO.getPage()))
              .onFailure(err -> LOGGER.error("Search execution failed: {}", err.getMessage()));

    } catch (Exception e) {
      LOGGER.error("Error during postSearch: {}", e.getMessage(), e);
      return Future.failedFuture(new DxBadRequestException("Failed to process search request"));
    }
  }

  private QueryModel buildQueryModel(QueryDecoderRequestDTO requestDTO) {
    String requestType = requestDTO.getRequestType();
    QueryDecoder queryDecoder = new QueryDecoder();

    if ("organisationAssetSearch".equalsIgnoreCase(requestType)) {
      return queryDecoder.getOrganisationAssetsQuery(requestDTO);
    } else if ("platformAssetSearch".equalsIgnoreCase(requestType)) {
      return queryDecoder.getPlatformAssetsQuery(requestDTO);
    } else if ("search".equalsIgnoreCase(requestType)) {
      return queryDecoder.getQueryModel(requestDTO);
    } else {
      throw new DxBadRequestException("Unsupported request type: {}" + requestType);
    }
  }

  private void applySorting(QueryModel queryModel, QueryDecoderRequestDTO requestDTO) {
    List<OrderBy> sortList = requestDTO.getSort();
    if (sortList != null && !sortList.isEmpty()) {
      Map<String, String> sortFields = sortList.stream()
              .collect(Collectors.toMap(OrderBy::getColumn, sort -> sort.getDirection().toString()));
      queryModel.setSortFields(sortFields);
    }
  }

  @Override
  public Future<ResponseModel> postCount(QueryDecoderRequestDTO queryDecoderRequestDTO) {
    try {
      // Build and log search type for traceability
      String searchType = queryDecoderRequestDTO.getSearchType();
      LOGGER.info("count search type {}", searchType);

      // Use QueryDecoderNew to build QueryModel
      QueryDecoder queryDecoder = new QueryDecoder();
      QueryModel queryModel = queryDecoder.getQueryModel(queryDecoderRequestDTO);

      // Set aggregation specific to count
      queryModel.setAggregations(List.of(queryDecoder.setCountAggregations()));

      // Run ES query
      return elasticsearchService
          .search(docIndex, queryModel, COUNT_AGGREGATION_ONLY)
          .map(ResponseModel::new)
          .onFailure(err -> LOGGER.error("Count execution failed: {}", err.getMessage()));
    } catch (Exception e) {
      LOGGER.error("Error during postCount: {}", e.getMessage(), e);
      return Future.failedFuture(new DxBadRequestException("Failed to process count request"));
    }
  }
}
