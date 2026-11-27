package org.cdpg.dx.aaa.shareAssets.handler;

import static org.cdpg.dx.aaa.common.Constants.PROVIDER_USER_ID;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

/**
 * Ensures the item referenced by a share-assets request exists and is owned by the caller.
 *
 * <p>The itemId is read from the JSON body (share / revoke) or, when there is no body, from the
 * {@code itemId} query parameter (list). For delegated calls the principal's sub is the delegator,
 * so a delegatee can manage shares only on the delegator's own items.
 */
public class ItemOwnershipCheck implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(ItemOwnershipCheck.class);

  private final ElasticsearchService elasticsearchService;
  private final String docIndex;

  public ItemOwnershipCheck(ElasticsearchService elasticsearchService, String docIndex) {

    this.elasticsearchService = elasticsearchService;
    this.docIndex = docIndex;
  }

  @Override
  public void handle(RoutingContext ctx) {

    String itemId = extractItemId(ctx);

    try {
      UUID.fromString(itemId);
    } catch (Exception e) {
      ctx.fail(new DxBadRequestException("Missing or invalid itemId"));
      return;
    }

    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    String callerId = user.sub() != null ? user.sub().toString() : null;

    fetchOwnerId(itemId)
        .onSuccess(
            ownerId -> {
              if (ownerId == null || callerId == null || !ownerId.equalsIgnoreCase(callerId)) {
                LOGGER.warn("User {} is not the owner of item {}", callerId, itemId);
                ctx.fail(new DxForbiddenException("User is not the owner of the asset"));
                return;
              }
              ctx.next();
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed validating item ownership for {} : {}", itemId, err.getMessage(), err);
              ctx.fail(err);
            });
  }

  private static String extractItemId(RoutingContext ctx) {

    JsonObject body = null;
    try {
      body = ctx.body() != null ? ctx.body().asJsonObject() : null;
    } catch (Exception ignored) {
      // not a JSON body; fall back to query param
    }

    if (body != null && body.getString("itemId") != null) {
      return body.getString("itemId");
    }
    return ctx.queryParams().get("itemId");
  }

  /** Resolves the item's owner id; fails with 404 if the item does not exist. */
  private Future<String> fetchOwnerId(String itemId) {

    QueryModel queryModel = new QueryDecoder().getItemIdQueryModel(itemId);

    return elasticsearchService
        .getSingleDocument(docIndex, queryModel.getQueries())
        .compose(
            response -> {
              if (response.getDocId() == null) {
                LOGGER.warn("Item does not exist : {}", itemId);
                return Future.failedFuture(new DxNotFoundException("Item not found"));
              }
              JsonObject source = response.getSource();
              return Future.succeededFuture(
                  source != null ? source.getString(PROVIDER_USER_ID) : null);
            });
  }
}
