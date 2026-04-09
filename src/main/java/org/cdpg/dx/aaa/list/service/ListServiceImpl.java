package org.cdpg.dx.aaa.list.service;

import static org.cdpg.dx.database.elastic.util.Constants.*;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.model.ElasticsearchSearchResult;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class ListServiceImpl implements ListService {
  private static final Logger LOGGER = LogManager.getLogger(ListServiceImpl.class);
  private final QueryDecoder queryDecoder;
  ElasticsearchService elasticsearchService;
  String docIndex;

  public ListServiceImpl(ElasticsearchService elasticsearchService, String docIndex) {
    this.elasticsearchService = elasticsearchService;
    this.queryDecoder = new QueryDecoder();
    this.docIndex = docIndex;
  }

  @Override
  public Future<ResponseModel> getAvailableFilters(QueryDecoderRequestDTO queryDecoderRequestDTO) {

    if (queryDecoderRequestDTO.getFilter() == null
        || queryDecoderRequestDTO.getFilter().isEmpty()) {
      return Future.failedFuture(new DxBadRequestException("Missing or empty 'filter' array"));
    }
    QueryDecoder queryDecoder = new QueryDecoder();
    QueryModel queryModel;
    try {
      queryModel = queryDecoder.listMultipleItemTypesQuery(queryDecoderRequestDTO);
    } catch (DxBadRequestException e) {
      return Future.failedFuture(e);
    }
    return elasticsearchService
        .search(docIndex, queryModel, AGGREGATION_LIST)
        .map(
            searchResult ->
                new ResponseModel(searchResult.getResults(), searchResult.getAggregations()))
        .onFailure(err -> LOGGER.error(new DxBadRequestException(err.getMessage())));
  }
}
