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
import org.cdpg.dx.common.exception.DxEsException;
import org.cdpg.dx.database.elastic.model.ElasticsearchSearchResult;
import org.cdpg.dx.database.postgres.models.OrderBy;
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

      return elasticsearchService
          .search(docIndex, queryModel, SOURCE_ONLY)
          .map(
              searchResult ->
                  new ResponseModel(
                      searchResult.getResults(),
                      requestDTO.getSize(),
                      requestDTO.getPage(),
                      searchResult.getTotalHits()))
          .onFailure(err -> LOGGER.error("Search execution failed: {}", err.getMessage()));

    } catch (DxBadRequestException bre) {
      // These come from decorators or buildQueryModel
      LOGGER.error("Search request validation failed: {}", bre.getMessage());
      return Future.failedFuture(bre);

    } catch (DxEsException esEx) {
      // ES-related or decorator-originated business validation errors
      LOGGER.error("Search query construction failed: {}", esEx.getMessage());
      return Future.failedFuture(new DxBadRequestException(esEx.getMessage()));

    } catch (Exception e) {
      // Any unexpected internal error
      LOGGER.error("Unexpected error during postSearch: {}", e.getMessage(), e);
      return Future.failedFuture(new DxBadRequestException("Failed to process search request"));
    }
  }

  private QueryModel buildQueryModel(QueryDecoderRequestDTO requestDTO) {
    String requestType = requestDTO.getRequestType();
    QueryDecoder queryDecoder = new QueryDecoder();

    try {
      if ("organisationAssetSearch".equalsIgnoreCase(requestType)) {
        return queryDecoder.getOrganisationAssetsQuery(requestDTO);

      } else if ("platformAssetSearch".equalsIgnoreCase(requestType)) {
        return queryDecoder.getPlatformAssetsQuery(requestDTO);

      } else if ("search".equalsIgnoreCase(requestType)) {
        return queryDecoder.getQueryModel(requestDTO);

      } else {
        throw new DxBadRequestException("Unsupported request type: {}" + requestType);
      }

    } catch (DxEsException esEx) {
      // Any ES-specific validation error from decorators
      LOGGER.error("QueryModel building failed (ES validation): {}", esEx.getMessage());
      throw new DxBadRequestException(esEx.getMessage(), esEx);

    } catch (DxBadRequestException bre) {
      // Already a 400 – just propagate
      LOGGER.error("QueryModel build failed (Bad request): {}", bre.getMessage());
      throw bre;

    } catch (Exception ex) {
      // Unexpected, internal error
      LOGGER.error("Unexpected error while building QueryModel: {}", ex.getMessage(), ex);
      throw new DxBadRequestException("Failed to build query model");
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
      String searchType = queryDecoderRequestDTO.getSearchType();
      LOGGER.info("count search type {}", searchType);

      QueryDecoder queryDecoder = new QueryDecoder();
      QueryModel queryModel = queryDecoder.getQueryModel(queryDecoderRequestDTO);

      // Set count aggregation
      queryModel.setAggregations(List.of(queryDecoder.setCountAggregations()));

      return elasticsearchService
          .search(docIndex, queryModel, COUNT_AGGREGATION_ONLY)
          .map(
              searchResult ->
                  new ResponseModel(searchResult.getResults(), searchResult.getAggregations()))
          .onFailure(err -> LOGGER.error("Count execution failed: {}", err.getMessage()));

    } catch (DxBadRequestException bre) {
      // Thrown by QueryDecoder → return exact message
      LOGGER.error("Count request validation failed: {}", bre.getMessage());
      return Future.failedFuture(bre);

    } catch (DxEsException esEx) {
      // Decorator / business validation failures → map to BadRequest
      LOGGER.error("Count query construction failed: {}", esEx.getMessage());
      return Future.failedFuture(new DxBadRequestException(esEx.getMessage()));

    } catch (Exception e) {
      // Unexpected system errors
      LOGGER.error("Unexpected error during postCount: {}", e.getMessage(), e);
      return Future.failedFuture(new DxBadRequestException("Failed to process count request"));
    }
  }

}
