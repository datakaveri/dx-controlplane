package org.cdpg.dx.aaa.list.service;

import static org.cdpg.dx.database.elastic.util.Constants.*;

import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.shareAssets.service.VisibilityService;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class ListServiceImpl implements ListService {

  private static final Logger LOGGER = LogManager.getLogger(ListServiceImpl.class);

  private final QueryDecoder queryDecoder;
  private final ElasticsearchService elasticsearchService;
  private final VisibilityService visibilityService;
  private final String docIndex;

  public ListServiceImpl(ElasticsearchService elasticsearchService,
                         VisibilityService visibilityService, String docIndex) {
    this.elasticsearchService = elasticsearchService;
    this.visibilityService = visibilityService;
    this.queryDecoder = new QueryDecoder();
    this.docIndex = docIndex;
  }

  @Override
  public Future<ResponseModel> getAvailableFilters(QueryDecoderRequestDTO queryDecoderRequestDTO) {

    if (queryDecoderRequestDTO.getFilter() == null
        || queryDecoderRequestDTO.getFilter().isEmpty()) {

      return Future.failedFuture(new DxBadRequestException("Missing or empty 'filter' array"));
    }

    return enrichSharedAssets(queryDecoderRequestDTO)
        .compose(
            enrichedRequest -> {
              QueryModel queryModel;

              try {
                queryModel = queryDecoder.listMultipleItemTypesQuery(enrichedRequest);

              } catch (DxBadRequestException e) {
                return Future.failedFuture(e);
              }

              return elasticsearchService
                  .search(docIndex, queryModel, AGGREGATION_LIST)
                  .map(
                      searchResult ->
                          new ResponseModel(
                              searchResult.getResults(), searchResult.getAggregations()));
            })
        .onFailure(
            err -> LOGGER.error("Available filters request failed: {}", err.getMessage(), err));
  }

  private Future<QueryDecoderRequestDTO> enrichSharedAssets(QueryDecoderRequestDTO requestDTO) {

    if (requestDTO.getAccessPolicyRequest() == null
        || requestDTO.getAccessPolicyRequest().getSub() == null) {

      return Future.succeededFuture(requestDTO);
    }

    UUID userId = UUID.fromString(requestDTO.getAccessPolicyRequest().getSub());

    String orgId = requestDTO.getOrganisationId();

    return visibilityService
        .getAssetsSharedWithMe(userId, orgId)
        .map(
            visibilityList -> {
              List<String> sharedIds =
                  visibilityList.stream().map(v -> v.getItemId().toString()).distinct().toList();

              requestDTO.getAccessPolicyRequest().setSharedItemIds(sharedIds);

              return requestDTO;
            });
  }
}
