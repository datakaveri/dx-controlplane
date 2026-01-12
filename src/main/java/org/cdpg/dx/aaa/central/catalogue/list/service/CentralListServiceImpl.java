package org.cdpg.dx.aaa.central.catalogue.list.service;

import static org.cdpg.dx.database.elastic.util.Constants.AGGREGATION_LIST;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.central.service.CentralElasticsearchService;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;
import org.cdpg.dx.database.elastic.model.QueryModel;

public class CentralListServiceImpl implements CentralListService {
  private static final Logger LOGGER = LogManager.getLogger(CentralListServiceImpl.class);
  private final QueryDecoder queryDecoder;
  CentralElasticsearchService centralElasticsearchService;
  String docIndex;

  public CentralListServiceImpl(CentralElasticsearchService centralElasticsearchService, String docIndex) {
    this.centralElasticsearchService = centralElasticsearchService;
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
    QueryModel queryModel = queryDecoder.listMultipleItemTypesQuery(queryDecoderRequestDTO);
    return centralElasticsearchService
        .search(docIndex, queryModel, AGGREGATION_LIST)
        .map(ResponseModel::new)
        .onFailure(err -> LOGGER.error(new DxBadRequestException(err.getMessage())));
  }
}
