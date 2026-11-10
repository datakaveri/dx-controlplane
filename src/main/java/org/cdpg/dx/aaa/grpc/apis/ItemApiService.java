package org.cdpg.dx.aaa.grpc.apis;

import io.grpc.stub.StreamObserver;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apis.catalogue.v1.GetItemRequest;
import org.cdpg.dx.apis.catalogue.v1.GetItemResponse;
import org.cdpg.dx.apis.catalogue.v1.ItemServiceGrpc;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

/**
 * Serves the shared {@code dx.apis.catalogue.v1.ItemService} contract (dx-common
 * src/main/proto/dx/apis/catalogue/v1/item.proto), which dx-catalogue-go serves too.
 *
 * <p>Unlike {@code CatItemGrpcService}, which answers as an end user with access checks, this
 * returns the stored item to a trusted service. It runs the same item-id query as {@code
 * ItemService#getItem}, so {@code item_json} is the document {@code GET /cat/item} returns as
 * {@code results[0]}.
 */
public class ItemApiService extends ItemServiceGrpc.ItemServiceImplBase {

  private static final Logger LOGGER = LogManager.getLogger(ItemApiService.class);

  private final Context vertxContext;
  private final ElasticsearchService elasticsearchService;
  private final String docIndex;

  /** Builds the service over the catalogue index {@code docIndex}. */
  public ItemApiService(Vertx vertx, ElasticsearchService elasticsearchService, String docIndex) {
    this.vertxContext = vertx.getOrCreateContext();
    this.elasticsearchService = elasticsearchService;
    this.docIndex = docIndex;
  }

  @Override
  public void getItem(GetItemRequest request, StreamObserver<GetItemResponse> observer) {
    String itemId = request.getItemId();
    if (!isUuid(itemId)) {
      observer.onError(GrpcErrors.invalid("item_id must be a UUID"));
      return;
    }
    vertxContext.runOnContext(
        ignored ->
            elasticsearchService
                .getSingleDocument(
                    docIndex, new QueryDecoder().getItemIdQueryModel(itemId).getQueries())
                .onSuccess(
                    es -> {
                      if (es == null || es.getDocId() == null || es.getSource() == null) {
                        observer.onError(GrpcErrors.notFound("item " + itemId + " not found"));
                        return;
                      }
                      String itemJson = es.getSource().encode();
                      observer.onNext(GetItemResponse.newBuilder().setItemJson(itemJson).build());
                      observer.onCompleted();
                    })
                .onFailure(
                    err -> {
                      LOGGER.error("GetItem failed for itemId={}: {}", itemId, err.getMessage());
                      observer.onError(GrpcErrors.toStatus(err));
                    }));
  }

  static boolean isUuid(String s) {
    if (s == null || s.isBlank()) {
      return false;
    }
    try {
      UUID.fromString(s);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
