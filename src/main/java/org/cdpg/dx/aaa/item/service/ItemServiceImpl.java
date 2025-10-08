package org.cdpg.dx.aaa.item.service;

import static org.cdpg.dx.aaa.common.Constants.COS;
import static org.cdpg.dx.aaa.common.Constants.FIELD;
import static org.cdpg.dx.aaa.common.Constants.PRIVATE;
import static org.cdpg.dx.aaa.common.Constants.PROVIDER;
import static org.cdpg.dx.aaa.common.Constants.PROVIDER_USER_ID;
import static org.cdpg.dx.aaa.common.Constants.RESOURCE_GRP;
import static org.cdpg.dx.aaa.common.Constants.RESOURCE_SVR;
import static org.cdpg.dx.aaa.common.Constants.RESTRICTED;
import static org.cdpg.dx.aaa.common.Constants.VALUE;
import static org.cdpg.dx.database.elastic.util.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.item.util.ItemFactory;
import org.cdpg.dx.aaa.item.util.PatchItemRequest;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.service.AccessRequestService;
import org.cdpg.dx.acl.accessRequest.service.impl.AccessRequestServiceImpl;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.elastic.util.QueryType;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class ItemServiceImpl implements ItemService {
  private static final Logger LOGGER = LogManager.getLogger(ItemServiceImpl.class);
  private final String docIndex;
  private final String apdURL;
  private final AccessRequestService accessRequestService;
  private final KeycloakUserService keycloakUserService;
  private final WebClient client;
  ElasticsearchService elasticsearchService;
  QueryDecoder queryDecoder = new QueryDecoder();

  public ItemServiceImpl(ElasticsearchService elasticsearchService,
                         KeycloakUserService keycloakUserService,
                         AccessRequestDao accessRequestDao,
                         WebClient webClient, String docIndex, String apdURL) {
    this.elasticsearchService = elasticsearchService;
    this.accessRequestService = new AccessRequestServiceImpl(this, accessRequestDao);
    this.keycloakUserService = keycloakUserService;
    this.client = webClient;
    this.docIndex = docIndex;
    this.apdURL = apdURL;
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
    QueryDecoder queryDecoder = new QueryDecoder();
    QueryModel queryModel = queryDecoder.getItemIdQueryModel(request.getItemId());

    LOGGER.debug("Retrieving item with ID: {}", queryModel.toJson());

    Future<ElasticsearchResponse> elResponse = elasticsearchService
        .getSingleDocument(docIndex, queryModel.getQueries());

    return elResponse.compose(elasticResponse -> {
      int totalHits = ElasticsearchResponse.getTotalHits();
      if (totalHits == 0) {
        LOGGER.warn("Item with ID {} does not exist", request.getItemId());
        ResponseModel responseModel = new ResponseModel(List.of(elasticResponse));
        responseModel.setTotalHits(totalHits);
        return Future.succeededFuture(responseModel);
      }

      JsonObject source = elasticResponse.getSource();
      String accessPolicy = source.getString(ACCESS_POLICY);
      String ownerUserId = source.getString(PROVIDER_USER_ID);
      if (accessPolicy.equalsIgnoreCase(PRIVATE)) {
        return getResponseWhenResourceIsPrivate(ownerUserId, request, elasticResponse, totalHits);
      }
      return getResponseWhenResourceIsPublic(accessPolicy, totalHits, elasticResponse);
    });
  }

  @Override
  public Future<ResponseModel> getItemWithAccessChecks(GetItemRequest request) {
    QueryDecoder queryDecoder = new QueryDecoder();
    QueryModel queryModel = queryDecoder.getItemIdQueryModel(request.getItemId());

    LOGGER.debug("Retrieving item with ID: {}", queryModel.toJson());

    Future<ElasticsearchResponse> elResponse = elasticsearchService
        .getSingleDocument(docIndex, queryModel.getQueries());

    return elResponse.compose(elasticResponse -> {
      int totalHits = ElasticsearchResponse.getTotalHits();
      if (totalHits == 0) {
        LOGGER.warn("Item with ID {} does not exist", request.getItemId());
        ResponseModel responseModel = new ResponseModel(List.of(elasticResponse));
        responseModel.setTotalHits(totalHits);
        return Future.succeededFuture(responseModel);
      }

      JsonObject source = elasticResponse.getSource();
      String accessPolicy = source.getString(ACCESS_POLICY);
      String ownerUserId = source.getString(PROVIDER_USER_ID);
      if (accessPolicy.equalsIgnoreCase(PRIVATE)) {
        return getResponseWhenResourceIsPrivate(ownerUserId, request, elasticResponse, totalHits);
      } else if (accessPolicy.equalsIgnoreCase(RESTRICTED)) {
        return getResponseWhenResourceIsRestricted(ownerUserId, request, totalHits,
            elasticResponse);
      }
      return getResponseWhenResourceIsPublic(accessPolicy, totalHits, elasticResponse);
    });
  }

  public Future<ResponseModel> getResponseWhenResourceIsPrivate(String ownerUserId,
                                                                GetItemRequest request,
                                                                ElasticsearchResponse response,
                                                                int totalHits) {
    if (request.getSubId() == null || request.getSubId().isEmpty()) {
      LOGGER.warn("Private item access denied: Missing token (subId is null/empty)");
      return Future.failedFuture(
          new DxUnauthorizedException("Authorization token is required for private item"));
    }
    if (ownershipCheck(ownerUserId, request.getSubId(), request.getRoles())) {
      LOGGER.debug("Ownership check passed for item with ID: {}", request.getItemId());
      ResponseModel responseModel = new ResponseModel(List.of(response), 1, 1);
      responseModel.setTotalHits(totalHits);
      return Future.succeededFuture(responseModel);
    } else {
      LOGGER.warn("Ownership check failed for item with ID: {}", request.getItemId());
      return Future.failedFuture(new DxForbiddenException("User doesn't have access to this item"));
    }
  }

  private boolean ownershipCheck(String ownerUserId, String subId, List<String> roles) {
    // Allow admin roles to bypass ownership restrictions
    if (roles != null && roles.stream().anyMatch(
        role -> role.equalsIgnoreCase(COS_ADMIN) ||
            role.equalsIgnoreCase(ORG_ADMIN))) {
      LOGGER.info("Ownership check bypassed for admin role(s):");
      return true;
    }

    if (subId.isEmpty()) {
      LOGGER.warn("Ownership check failed: No subId provided for private access policy");
      return false;
    }
    if (!ownerUserId.equalsIgnoreCase(subId)) {
      LOGGER.warn("Ownership check failed: User {} does not own the item", subId);
      return false;
    }
    return true;
  }

  public Future<ResponseModel> getResponseWhenResourceIsPublic(String accessPolicy, int totalHits
      , ElasticsearchResponse response) {
    LOGGER.info("Ownership and access check not required for access policy " +
        "'{}'", accessPolicy);
    ResponseModel responseModel = new ResponseModel(List.of(response), 1, 1);
    responseModel.setTotalHits(totalHits);
    return Future.succeededFuture(responseModel);
  }

  public Future<ResponseModel> getResponseWhenResourceIsRestricted(String ownerUserId,
                                                                   GetItemRequest request,
                                                                   int totalHits,
                                                                   ElasticsearchResponse response) {
    if (request.getSubId() == null || request.getSubId().isEmpty()) {
      LOGGER.warn("Restricted item access denied: Missing token (subId is null/empty)");
      return Future.failedFuture(
          new DxUnauthorizedException("Authorization token is required for restricted item"));
    }

    // Allow the owner direct access
    if (request.getSubId().equalsIgnoreCase(ownerUserId)) {
      LOGGER.debug("Restricted item access granted: User {} is the owner of item {}",
          request.getSubId(), request.getItemId());
      ResponseModel responseModel = new ResponseModel(List.of(response), 1, 1);
      responseModel.setTotalHits(totalHits);
      return Future.succeededFuture(responseModel);
    }

    // Fetch apdUrl from item source
    String apdUrl = response.getSource().getString(APD_URL);
    String itemType = response.getSource().getJsonArray(TYPE).getString(0);
    if (apdUrl == null || apdUrl.isBlank()) {
      LOGGER.error("Restricted item missing apdUrl in metadata");
      return Future.failedFuture(new DxForbiddenException("Access denied, APD URL missing"));
    }

    if (apdUrl.equals(apdURL)) { // apdURL = default apd
      return accessRequestService.checkAccessRequest(UUID.fromString(request.getSubId()),
              request.getItemId())
          .compose(v -> succeededResponse(response, totalHits))
          .recover(failure -> {
            LOGGER.error("Error during restricted access check: {}", failure.getMessage());
            return Future.failedFuture(failure);
          });
    }

    //else Verify via external APD
    UUID requestId = UUID.fromString(request.getSubId());
    UUID ownerId = UUID.fromString(ownerUserId);
    Future<DxUser> requesterFut = keycloakUserService.getUserById(requestId);
    Future<DxUser> ownerFut = keycloakUserService.getUserById(ownerId);

    return Future.all(requesterFut, ownerFut).compose(cf -> {
      DxUser requester = requesterFut.result();
      DxUser owner = ownerFut.result();
      return verifyWithApd(apdUrl, requester, owner, request, itemType, totalHits, response);
    });
  }


  // --- Helpers ---
  private Future<ResponseModel> succeededResponse(ElasticsearchResponse response, int totalHits) {
    ResponseModel responseModel = new ResponseModel(List.of(response), 1, 1);
    responseModel.setTotalHits(totalHits);
    return Future.succeededFuture(responseModel);
  }

  private Future<ResponseModel> verifyWithApd(String apdUrl, DxUser requester,
                                              DxUser owner, GetItemRequest request,
                                              String itemType, int totalHits,
                                              ElasticsearchResponse response) {
    JsonObject verifyPayload = new JsonObject()
        .put(USER, buildUserBlock(requester))
        .put(OWNER, buildUserBlock(owner))
        .put(ITEM, new JsonObject()
            .put(ITEM_ID, request.getItemId())
            .put(ITEM_TYPE, itemType));

    return client.postAbs(HTTPS + apdUrl + "/verify")
        .putHeader(AUTHORIZATION_KEY, BEARER_KEY + " " + request.getToken())
        .sendJsonObject(verifyPayload)
        .compose(httpResponse -> {
          if (httpResponse.statusCode() == 200) {
            JsonObject body = httpResponse.bodyAsJsonObject();
            String decision = body.getString(TYPE);

            if ("urn:apd:Allow".equalsIgnoreCase(decision)) {
              LOGGER.info("APD allowed access for user {}", request.getSubId());
              return succeededResponse(response, totalHits);
            } else {
              LOGGER.warn("APD denied access for user {} with decision {}",
                  request.getSubId(), decision);
              return Future.failedFuture(new DxForbiddenException("Access denied by APD"));
            }
          } else {
            LOGGER.error("APD verify call failed: status {}, body {}",
                httpResponse.statusCode(), httpResponse.bodyAsString());
            return Future.failedFuture(new DxForbiddenException("APD verification failed"));
          }
        })
        .recover(failure -> {
          LOGGER.error("Error during APD verification: {}", failure.getMessage());
          return Future.failedFuture(new DxForbiddenException("APD verification failed"));
        });
  }

  // Helper to convert DxUser → APD user block
  private JsonObject buildUserBlock(DxUser user) {
    return new JsonObject()
        .put("id", user.sub().toString())
        .put("name", new JsonObject()
            .put("firstName", user.givenName())
            .put("lastName", user.familyName()))
        .put("email", user.email());
  }

  @Override
  public Future<ElasticsearchResponse> patchItem(PatchItemRequest patchItemRequest) {
    LOGGER.debug("Updating item: {}", patchItemRequest.getItemId());
    Promise<ElasticsearchResponse> promise = Promise.promise();

    if (patchItemRequest.getItemId() == null || patchItemRequest.getItemId().isBlank()) {
      return Future.failedFuture("ID not present in request");
    }

    List<String> roles = patchItemRequest.getAllowedRoles();
    QueryModel queryModel;
    if (roles.contains(COS_ADMIN)) {
      queryModel = queryDecoder.getItemIdQueryModel(patchItemRequest.getItemId());
    } else if (roles.contains(ORG_ADMIN)) {
      queryModel = queryDecoder.getItemIdOrgIdQueryModel(patchItemRequest.getItemId(),
          patchItemRequest.getOrgId());
    } else if (roles.contains(PROVIDER)) {
      queryModel = queryDecoder.getItemIdOwnerIdQueryModel(patchItemRequest.getItemId(),
          patchItemRequest.getUserId());
      // Provider restriction: only allow 'dataUploadStatus'
      JsonObject patchBody = patchItemRequest.getRequestBody();
      if (!patchBody.containsKey("dataUploadStatus") || patchBody.size() != 1) {
        return Future.failedFuture(
            new DxForbiddenException("Providers can only update dataUploadStatus"));
      }
    } else {
      return Future.failedFuture(new DxForbiddenException("User role not permitted to patch item"));
    }
    LOGGER.debug("query: " + queryModel.getQueries().toElasticsearchQuery());
    String id = patchItemRequest.getItemId();
    elasticsearchService
        .getSingleDocument(docIndex, queryModel.getQueries())
        .onSuccess(
            result -> {
              if (ElasticsearchResponse.getTotalHits() < 1) {
                String errorMsg;

                if (roles.contains(COS_ADMIN)) {
                  errorMsg = "Item not found for update";
                } else if (roles.contains(ORG_ADMIN)) {
                  errorMsg = "No item found for update under your organization";
                } else if (roles.contains(PROVIDER)) {
                  errorMsg = "No item found owned by you for update";
                } else {
                  errorMsg = "Item not found or access denied";
                }

                LOGGER.debug("Item with ID {} not found for update. Role: {}, message: {}",
                    id, roles, errorMsg);
                promise.fail(new DxBadRequestException(errorMsg));
              } else {
                LOGGER.debug("Update item with ID: {}", id);
                String docId = result.getDocId();
                LOGGER.debug("Result {}", result.getSource());
                QueryModel patchQueryModel = new QueryModel();
                patchQueryModel.createQueryModelFromDocument(patchItemRequest.getRequestBody());
                elasticsearchService.updateDocument(docIndex, docId, patchQueryModel)
                    .onSuccess(
                        v -> {
                          LOGGER.debug("Item with ID {} updated successfully", id);
                          promise.complete(result);
                        })
                    .onFailure(
                        failure -> {
                          LOGGER.error(
                              "Failed to update item with ID {}: {}",
                              id,
                              failure.getMessage());
                          promise.fail(new DxBadRequestException(
                              "Failed to update item: " + failure.getMessage()));
                        });
              }
            })
        .onFailure(promise::fail);

    return promise.future();

  }

  @Override
  public Future<ElasticsearchResponse> deleteItem(String id) {
    LOGGER.debug("Deleting item with ID: {}", id);
    Promise<ElasticsearchResponse> promise = Promise.promise();

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
                promise.fail(
                    new DxConflictException("Item has associated entities and cannot be deleted"));
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
                          promise.complete(result);
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

  @Override
  public Future<Void> ownerShipTransfer(String oldOwnerId, String newOwnerId,
                                        String organizationId) {
    LOGGER.debug("Starting ownership transfer from {} to {} in organization {}",
        oldOwnerId, newOwnerId, organizationId);

    if (isNullOrEmpty(oldOwnerId)) {
      String errorMsg = "Old owner ID cannot be null or empty";
      LOGGER.error(errorMsg);
      return Future.failedFuture(errorMsg);
    }

    if (isNullOrEmpty(newOwnerId)) {
      String errorMsg = "New owner ID cannot be null or empty";
      LOGGER.error(errorMsg);
      return Future.failedFuture(errorMsg);
    }

    if (isNullOrEmpty(organizationId)) {
      String errorMsg = "Organization ID cannot be null or empty";
      LOGGER.error(errorMsg);
      return Future.failedFuture(errorMsg);
    }

    if (oldOwnerId.equals(newOwnerId)) {
      String errorMsg = "Old owner ID and new owner ID cannot be the same";
      LOGGER.error(errorMsg);
      return Future.failedFuture(errorMsg);
    }

    try {
      QueryModel ownerShipTransferQuery =
          queryDecoder.ownerShipTransferQuery(oldOwnerId, newOwnerId, organizationId);
      LOGGER.debug("Query for ownership transfer: {}",
          ownerShipTransferQuery.getQueries().toJson());
      return elasticsearchService.updateDocumentsByQuery(ownerShipTransferQuery.getQueries(),
              docIndex)
          .onSuccess(
              result -> LOGGER.debug("Ownership transfer from {} to {} completed successfully",
                  oldOwnerId, newOwnerId))
          .onFailure(failure -> {
            LOGGER.error("Ownership transfer from {} to {} failed: {}",
                oldOwnerId, newOwnerId, failure.getMessage());
            Future.failedFuture("Ownership transfer failed: " + failure.getMessage());
          })
          .mapEmpty();

    } catch (Exception e) {
      LOGGER.error(
          "Failed to create ownership transfer query for oldOwner: {}, newOwner: {}, org: {}",
          oldOwnerId, newOwnerId, organizationId, e);
      return Future.failedFuture("Failed to create ownership transfer query: " + e.getMessage());
    }
  }

  private boolean isNullOrEmpty(String str) {
    return str == null || str.trim().isEmpty();
  }

}
