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
  public Future<ResponseModel> postSearch(QueryDecoderRequestDTO queryDecoderRequestDTO) {
    try {
      // Derive SEARCH_TYPE from DTO for logging/debugging purposes
      String searchType = queryDecoderRequestDTO.getSearchType();
      LOGGER.info("search type {}", searchType);

      // Use the new decoder to get the QueryModel
      QueryDecoder queryDecoder = new QueryDecoder();
      QueryModel queryModel = queryDecoder.getQueryModel(queryDecoderRequestDTO);
      if (queryDecoderRequestDTO.getSort() != null && !queryDecoderRequestDTO.getSort().isEmpty()) {
        Map<String, String> sortFields =
            queryDecoderRequestDTO.getSort().stream()
                .collect(
                    Collectors.toMap(OrderBy::getColumn, sort -> sort.getDirection().toString()));
        queryModel.setSortFields(sortFields);
      }

      // Perform search
      return elasticsearchService
          .search(docIndex, queryModel, SOURCE_ONLY)
          .map(
              results ->
                  new ResponseModel(
                      results, queryDecoderRequestDTO.getSize(), queryDecoderRequestDTO.getPage()))
          .onFailure(err -> LOGGER.error("Search execution failed: {}", err.getMessage()));
    } catch (Exception e) {
      LOGGER.error("Error during postSearch: {}", e.getMessage(), e);
      return Future.failedFuture(new DxBadRequestException("Failed to process search request"));
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
