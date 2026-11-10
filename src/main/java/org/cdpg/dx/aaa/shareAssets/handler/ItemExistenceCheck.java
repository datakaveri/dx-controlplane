package org.cdpg.dx.aaa.shareAssets.handler;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class ItemExistenceCheck implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(ItemExistenceCheck.class);

  private final ElasticsearchService elasticsearchService;
  private final String docIndex;

  public ItemExistenceCheck(ElasticsearchService elasticsearchService, String docIndex) {

    this.elasticsearchService = elasticsearchService;
    this.docIndex = docIndex;
  }

  @Override
  public void handle(RoutingContext ctx) {

    String itemId = ctx.body().asJsonObject().getString("itemId");

    isItemExists(itemId)
        .onSuccess(
            exists -> {
              if (Boolean.FALSE.equals(exists)) {

                LOGGER.warn("Item does not exist : {}", itemId);

                ctx.fail(new DxNotFoundException("Item not found"));
                return;
              }

              ctx.next();
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed validating item existence for {} : {}", itemId, err.getMessage(), err);

              ctx.fail(err);
            });
  }

  private Future<Boolean> isItemExists(String itemId) {

    QueryDecoder queryDecoder = new QueryDecoder();

    QueryModel queryModel = queryDecoder.getItemIdQueryModel(itemId);

    return elasticsearchService
        .getSingleDocument(docIndex, queryModel.getQueries())
        .map(response -> response.getDocId() != null);
  }
}
