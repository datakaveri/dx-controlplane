package org.cdpg.dx.aaa.item.service;

import static org.cdpg.dx.aaa.common.Constants.*;
import static org.cdpg.dx.database.elastic.util.Constants.DETAIL_ITEM_NOT_FOUND;
import static org.cdpg.dx.database.elastic.util.Constants.ID_KEYWORD;
import static org.cdpg.dx.database.elastic.util.Constants.KEYWORD_KEY;
import static org.cdpg.dx.database.elastic.util.Constants.TYPE_KEYWORD;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.item.util.ItemFactory;
import org.cdpg.dx.aaa.item.util.PatchItemRequest;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.elastic.util.QueryType;

public class ItemServiceImpl implements ItemService {
  private static final Logger LOGGER = LogManager.getLogger(ItemServiceImpl.class);
  private final String docIndex;
  ElasticsearchService elasticsearchService;
  QueryDecoder queryDecoder = new QueryDecoder();

  public ItemServiceImpl(ElasticsearchService elasticsearchService, String docIndex) {
    this.elasticsearchService = elasticsearchService;
    this.docIndex = docIndex;
  }

  @Override
  public Future<Void> createItem(Item item) {
    Promise<Void> promise = Promise.promise();
    String id = item.getId();

    if (id == null || id.isBlank()) {
      return Future.failedFuture("ID not present in request");
    }

    QueryModel termQuery = new QueryModel(QueryType.TERM);
    termQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));

    elasticsearchService
        .getSingleDocument(docIndex, termQuery)
        .onSuccess(
            existingDoc -> {
              if (existingDoc != null && ElasticsearchResponse.getTotalHits() > 0) {
                LOGGER.warn("Item with ID {} already exists", id);
                promise.fail("Item with ID already exists");
              } else {
                QueryModel queryModel = new QueryModel();
                queryModel.createQueryModelFromDocument(item.toJson());
                elasticsearchService
                    .createDocuments(docIndex, Collections.singletonList(queryModel))
                    .onSuccess(v -> promise.complete())
                    .onFailure(promise::fail);
              }
            })
        .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<ResponseModel> getItem(GetItemRequest request) {
    Promise<ResponseModel> promise = Promise.promise();

    QueryDecoder queryDecoder = new QueryDecoder();
    QueryModel queryModel = queryDecoder.getItemSubQueryModel(request.getItemId());

    LOGGER.debug("Retrieving item with ID: {}", queryModel.toJson());

    elasticsearchService
        .getSingleDocument(docIndex, queryModel.getQueries())
        .onSuccess(
            response -> {
              int totalHits = ElasticsearchResponse.getTotalHits();
              if (totalHits == 0) {
                LOGGER.warn("Item with ID {} does not exist", request.getItemId());
                ResponseModel responseModel = new ResponseModel(List.of(response));
                responseModel.setTotalHits(totalHits);
                promise.complete(responseModel);
                return;
              }

              if (ownershipCheck(response, request.getSubId())) {
                LOGGER.debug("Ownership check passed for item with ID: {}", request.getItemId());
                ResponseModel responseModel = new ResponseModel(List.of(response));
                responseModel.setTotalHits(totalHits);
                promise.complete(responseModel);
              } else {
                LOGGER.warn("Ownership check failed for item with ID: {}", request.getItemId());
                promise.fail("Ownership check failed");
              }
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Error retrieving item with ID {}: {}", request.getItemId(), err.getMessage());
              promise.fail("Failed to retrieve item: " + err.getMessage());
            });

    return promise.future();
  }

  @Override
  public Future<Void> patchItem(PatchItemRequest patchItemRequest) {
    LOGGER.debug("Updating item: {}", patchItemRequest.getItemId());
    Promise<Void> promise = Promise.promise();

    if (patchItemRequest.getItemId() == null || patchItemRequest.getItemId().isBlank()) {
      return Future.failedFuture("ID not present in request");
    }

    QueryModel queryModel = queryDecoder.getItemSubQueryModel(patchItemRequest.getItemId(),patchItemRequest.getSubId());
    String id = patchItemRequest.getItemId();

    elasticsearchService
            .getSingleDocument(docIndex, queryModel.getQueries())
            .onSuccess(
                    result -> {
                      LOGGER.debug("Item with ID {} found for update", id);
                       if (ElasticsearchResponse.getTotalHits() < 1) {
                        LOGGER.debug("Item with ID {} not found for update", id);
                        promise.fail(new DxBadRequestException("Item not found for update"));
                      } else {
                        LOGGER.debug("Update item with ID: {}", id);
                        String docId = result.getDocId();
                        LOGGER.debug("Result {}",result.getSource());
                        QueryModel patchQueryModel = new QueryModel();
                        patchQueryModel.createQueryModelFromDocument(patchItemRequest.getRequestBody());
                        elasticsearchService.updateDocument(docIndex, docId, patchQueryModel)
                                .onSuccess(
                                        v -> {
                                          LOGGER.debug("Item with ID {} updated successfully", id);
                                          promise.complete();
                                        })
                                .onFailure(
                                        failure -> {
                                          LOGGER.error(
                                                  "Failed to update item with ID {}: {}",
                                                  id,
                                                  failure.getMessage());
                                          promise.fail(new DxBadRequestException("Failed to update item: " + failure.getMessage()));
                                        });
                      }
                    })
            .onFailure(promise::fail);

    return promise.future();

  }

  @Override
  public Future<Void> deleteItem(String id) {
    LOGGER.debug("Deleting item with ID: {}", id);
    Promise<Void> promise = Promise.promise();

    if (id == null || id.isBlank()) {
      return Future.failedFuture("ID not present in request");
    }

    QueryModel boolQuery = new QueryModel(QueryType.BOOL);
    QueryModel idTermQuery = new QueryModel(QueryType.TERM);
    idTermQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));
    QueryModel resourceGrpTermQuery = new QueryModel(QueryType.TERM);
    resourceGrpTermQuery.setQueryParameters(Map.of(FIELD, RESOURCE_GRP + KEYWORD_KEY, VALUE, id));
    QueryModel providerTermQuery = new QueryModel(QueryType.TERM);
    providerTermQuery.setQueryParameters(Map.of(FIELD, PROVIDER + KEYWORD_KEY, VALUE, id));
    QueryModel resourceSvrTermQuery = new QueryModel(QueryType.TERM);
    resourceSvrTermQuery.setQueryParameters(Map.of(FIELD, RESOURCE_SVR + KEYWORD_KEY, VALUE, id));
    QueryModel cosTermQuery = new QueryModel(QueryType.TERM);
    cosTermQuery.setQueryParameters(Map.of(FIELD, COS + KEYWORD_KEY, VALUE, id));

    boolQuery.setShouldQueries(
        List.of(
            idTermQuery,
            resourceGrpTermQuery,
            providerTermQuery,
            resourceSvrTermQuery,
            cosTermQuery));

    elasticsearchService
        .getSingleDocument(docIndex, boolQuery)
        .onSuccess(
            result -> {
              LOGGER.debug("Item with ID {} found for deletion", id);
              if (ElasticsearchResponse.getTotalHits() > 1) {
                LOGGER.debug("Item with ID {} has multiple associated entities", id);
                promise.fail(new DxConflictException("Item has associated entities and cannot be deleted"));
              } else if (ElasticsearchResponse.getTotalHits() < 1) {
                LOGGER.debug("Item with ID {} not found for deletion", id);
                promise.fail("Item not found for deletion");
              } else {
                LOGGER.debug("Deleting item with ID: {}", id);
                String docId = result.getDocId();
                elasticsearchService
                    .deleteDocument(docIndex, docId)
                    .onSuccess(
                        v -> {
                          LOGGER.debug("Item with ID {} deleted successfully", id);
                          promise.complete();
                        })
                    .onFailure(
                        failure -> {
                          LOGGER.error(
                              "Failed to delete item with ID {}: {}", id, failure.getMessage());
                          promise.fail("Failed to delete item: " + failure.getMessage());
                        });
              }
            })
        .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<Void> updateItem(Item item) {
    LOGGER.debug("Updating item with ID: {}", item.getId());
    Promise<Void> promise = Promise.promise();
    String id = item.getId();
    String type = item.getType().getFirst();

    if (id == null || id.isBlank() || type == null || type.isBlank()) {
      return Future.failedFuture("ID or Type missing in update request");
    }

    QueryModel boolQuery = new QueryModel(QueryType.BOOL);
    QueryModel termQuery = new QueryModel(QueryType.TERM);
    termQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));
    QueryModel matchQuery = new QueryModel(QueryType.MATCH);
    matchQuery.setQueryParameters(Map.of(FIELD, TYPE_KEYWORD, VALUE, type));

    boolQuery.setMustQueries(List.of(termQuery, matchQuery));

    elasticsearchService
        .getSingleDocument(docIndex, boolQuery)
        .onSuccess(
            getRes -> {
              if (getRes == null || ElasticsearchResponse.getTotalHits() == 0) {
                promise.fail("Item not found for update");
              } else {
                QueryModel queryModel = new QueryModel();
                queryModel.createQueryModelFromDocument(item.toJson());
                elasticsearchService
                    .updateDocument(docIndex, id, queryModel)
                    .onSuccess(v -> promise.complete())
                    .onFailure(promise::fail);
              }
            })
        .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<Item> itemWithTheNameExists(String type, String name) {
    Promise<Item> promise = Promise.promise();
    QueryModel queryModel = queryDecoder.buildGetItemWithNameExistsQuery(type, name);

    elasticsearchService
        .getSingleDocument(docIndex, queryModel)
        .onSuccess(
            result -> {
              if (result == null || result.getSource() == null || result.getSource().isEmpty()) {
                LOGGER.debug("Item with name '{}' of type '{}' not found", name, type);
                promise.fail(DETAIL_ITEM_NOT_FOUND);
              } else {
                LOGGER.debug("Item with name '{}' of type '{}' found", name, type);
                try {
                  Item item = ItemFactory.from(result.getSource());
                  promise.complete(item);
                } catch (Exception e) {
                  LOGGER.error("Failed to parse item from ES source: {}", e.getMessage());
                  promise.fail("Fail: Unable to parse existing item");
                }
              }
            })
        .onFailure(
            err -> {
              LOGGER.error("Error from elastic service for name '{}': {}", name, err.getMessage());
              promise.fail("Fail: Error while checking item existence");
            });

    return promise.future();
  }

  private boolean ownershipCheck(ElasticsearchResponse response, String subId) {
    JsonObject source = response.getSource();
    String accessPolicy = source.getString("accessPolicy");
    String ownerUserId = source.getString("ownerUserId");

    if ("private".equalsIgnoreCase(accessPolicy)) {
      if (subId.isEmpty()) {
        LOGGER.warn("Ownership check failed: No subId provided for private access policy");
        return false;
      }
      if (!ownerUserId.equalsIgnoreCase(subId)) {
        LOGGER.warn("Ownership check failed: User {} does not own the item", subId);
        return false;
      }
    } else {
      LOGGER.info("Ownership check not required for access policy '{}'", accessPolicy);
      return true;
    }
    return true;
  }
}
