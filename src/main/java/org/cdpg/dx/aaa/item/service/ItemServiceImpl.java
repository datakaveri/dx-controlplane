package org.cdpg.dx.aaa.item.service;

import static org.cdpg.dx.aaa.common.Constants.FIELD;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_APPS;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_DATA_BANK;
import static org.cdpg.dx.aaa.common.Constants.NAME;
import static org.cdpg.dx.aaa.common.Constants.PII;
import static org.cdpg.dx.aaa.common.Constants.PRIVATE;
import static org.cdpg.dx.aaa.common.Constants.PROVIDER;
import static org.cdpg.dx.aaa.common.Constants.PROVIDER_USER_ID;
import static org.cdpg.dx.aaa.common.Constants.RESTRICTED;
import static org.cdpg.dx.aaa.common.Constants.VALUE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.*;
import static org.cdpg.dx.catalogueService.config.Constants.*;
import static org.cdpg.dx.catalogueService.config.Constants.SHORT_DESCRIPTION;
import static org.cdpg.dx.database.elastic.util.Constants.ACCESS_POLICY;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;
import static org.cdpg.dx.database.elastic.util.Constants.COS_ADMIN;
import static org.cdpg.dx.database.elastic.util.Constants.DETAIL_ITEM_NOT_FOUND;
import static org.cdpg.dx.database.elastic.util.Constants.ID_KEYWORD;
import static org.cdpg.dx.database.elastic.util.Constants.KEYWORD_KEY;
import static org.cdpg.dx.database.elastic.util.Constants.ORG_ADMIN;
import static org.cdpg.dx.database.elastic.util.Constants.TYPE;
import static org.cdpg.dx.database.elastic.util.Constants.TYPE_KEYWORD;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.asset.models.AssetRequest;
import org.cdpg.dx.aaa.asset.models.AssetRequestResponse;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.interaction.model.InteractionAggregate;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.item.util.ItemFactory;
import org.cdpg.dx.aaa.item.util.PatchItemRequest;
import org.cdpg.dx.acl.accessRequest.dao.model.AssetType;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.model.VerifyPolicyDto;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.acl.policy.service.impl.PolicyServiceImpl;
import org.cdpg.dx.acl.rule.dao.AccessRuleDao;
import org.cdpg.dx.acl.rule.dao.impl.AccessRuleDaoImpl;
import org.cdpg.dx.catalogueService.config.Constants;
import org.cdpg.dx.catalogueService.models.Asset;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.database.elastic.model.BulkScriptUpdate;
import org.cdpg.dx.database.elastic.model.BulkSyncResult;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.elastic.util.QueryType;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class ItemServiceImpl implements ItemService {
  private static final Logger LOGGER = LogManager.getLogger(ItemServiceImpl.class);
  private final String docIndex;
  public final String deletedDocsIndex;
  private final PolicyVerifyService policyVerifyService;
  private final AccessRuleDao accessRuleDao;
  private final KeycloakUserService keycloakUserService;
  private final WebClient client;
  ElasticsearchService elasticsearchService;
  QueryDecoder queryDecoder = new QueryDecoder();

  public ItemServiceImpl(
      ElasticsearchService elasticsearchService,
      KeycloakUserService keycloakUserService,
      PostgresService postgresService,
      PolicyDao policyDao,
      WebClient webClient,
      String docIndex, String deletedDocsIndex,
      String apdURL) {
    this.accessRuleDao = new AccessRuleDaoImpl(postgresService);
    this.elasticsearchService = elasticsearchService;
    this.deletedDocsIndex = deletedDocsIndex;
    PolicyService policyService = new PolicyServiceImpl(this, keycloakUserService, policyDao,
        accessRuleDao, apdURL);
    this.policyVerifyService = new PolicyVerifyServiceImpl(policyService, webClient, apdURL);
    this.keycloakUserService = keycloakUserService;
    this.client = webClient;
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
              if (existingDoc != null && existingDoc.getDocId() != null) {
                LOGGER.warn("Item with ID {} already exists", id);
                promise.fail(new DxConflictException("Item with ID already exists"));
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
  public Future<JsonObject> getItemSource(String itemId) {
    return elasticsearchService
        .getDocumentById(docIndex, itemId)
        .map(ElasticsearchResponse::getSource);
  }

  @Override
  public Future<ResponseModel> getItem(GetItemRequest request) {
    QueryDecoder queryDecoder = new QueryDecoder();
    QueryModel queryModel = queryDecoder.getItemIdQueryModel(request.getItemId());

    LOGGER.debug("Retrieving item with ID: {}", queryModel.toJson());

    Future<ElasticsearchResponse> elResponse =
        elasticsearchService.getSingleDocument(docIndex, queryModel.getQueries());

    return elResponse.compose(
        elasticResponse -> {
          if (elasticResponse.getDocId() == null) {
            LOGGER.warn("Item with ID {} does not exist", request.getItemId());
            ResponseModel responseModel = new ResponseModel(List.of(elasticResponse), 1, 1, 0);
            return Future.succeededFuture(responseModel);
          }
          int totalHits = 1;

          JsonObject source = elasticResponse.getSource();
          String accessPolicy = source.getString(ACCESS_POLICY);
          String ownerUserId = source.getString(PROVIDER_USER_ID);
          String itemOrgId = source.getString(ORGANIZATION_ID);
          if (accessPolicy.equalsIgnoreCase(PRIVATE)) {
            return getResponseWhenResourceIsPrivate(
                ownerUserId, itemOrgId, request, elasticResponse, totalHits);
          }
          return getResponseWhenResourceIsPublic(accessPolicy, totalHits, elasticResponse);
        });
  }

  @Override
  public Future<ResponseModel> getItemWithAccessChecks(GetItemRequest request) {
    QueryDecoder queryDecoder = new QueryDecoder();
    QueryModel queryModel = queryDecoder.getItemIdQueryModel(request.getItemId());

    LOGGER.debug("Retrieving item with ID: {}", queryModel.toJson());

    Future<ElasticsearchResponse> elResponse =
        elasticsearchService.getSingleDocument(docIndex, queryModel.getQueries());

    return elResponse.compose(
        elasticResponse -> {
          if (elasticResponse.getDocId() == null) {
            LOGGER.warn("Item with ID {} does not exist", request.getItemId());
            ResponseModel responseModel = new ResponseModel(List.of(elasticResponse), 1, 1, 0);
            return Future.succeededFuture(responseModel);
          }
          int totalHits = 1;

          JsonObject source = elasticResponse.getSource();
          String accessPolicy = source.getString(ACCESS_POLICY);
          String ownerUserId = source.getString(PROVIDER_USER_ID);
          String itemOrganizationId = source.getString(ORGANIZATION_ID);
          if (accessPolicy.equalsIgnoreCase(PRIVATE)) {
            return getResponseWhenResourceIsPrivate(
                ownerUserId, itemOrganizationId, request, elasticResponse, totalHits);
          } else if (accessPolicy.equalsIgnoreCase(RESTRICTED)
              || accessPolicy.equalsIgnoreCase(PII)) {
            return getResponseWhenResourceIsRestricted(
                ownerUserId, itemOrganizationId, request, totalHits, elasticResponse);
          }
          return getResponseWhenResourceIsPublic(accessPolicy, totalHits, elasticResponse);
        });
  }

  public Future<ResponseModel> getResponseWhenResourceIsPrivate(
      String ownerUserId,
      String itemOrganizationId,
      GetItemRequest request,
      ElasticsearchResponse response,
      int totalHits) {
    if (request.getSubId() == null || request.getSubId().isEmpty()) {
      LOGGER.warn("Private item access denied: Missing token (subId is null/empty)");
      return Future.failedFuture(
          new DxUnauthorizedException("Authorization token is required for private item"));
    }
    if (ownershipCheck(
        ownerUserId,
        request.getSubId(),
        request.getRoles(),
        itemOrganizationId,
        request.getOrganizationId())) {

      boolean isOwner = ownerUserId.equalsIgnoreCase(request.getSubId());

      boolean isCosAdmin =
          request.getRoles() != null
              && request.getRoles().stream().anyMatch(role -> role.equalsIgnoreCase(COS_ADMIN));

      boolean isOrgAdmin =
          request.getRoles() != null
              && request.getRoles().stream().anyMatch(role -> role.equalsIgnoreCase(ORG_ADMIN))
              && Objects.equals(itemOrganizationId, request.getOrganizationId());

      boolean isAdmin = isCosAdmin || isOrgAdmin;

      setAccessFlags(response, isOwner, isAdmin);

      return Future.succeededFuture(new ResponseModel(List.of(response), 1, 1, totalHits));
    } else {
      LOGGER.warn("Ownership check failed for item with ID: {}", request.getItemId());
      return Future.failedFuture(new DxForbiddenException("User doesn't have access to this item"));
    }
  }

  private boolean ownershipCheck(
      String ownerUserId,
      String subId,
      List<String> roles,
      String ownerOrganizationId,
      String userOrganizationId) {
    // Allow admin roles to bypass ownership restrictions
    boolean isCosAdmin =
        roles != null && roles.stream().anyMatch(role -> role.equalsIgnoreCase(COS_ADMIN));

    if (isCosAdmin) {
      LOGGER.info("Ownership check bypassed for COS_ADMIN");
      return true;
    }

    boolean isOrgAdmin =
        roles != null && roles.stream().anyMatch(role -> role.equalsIgnoreCase(ORG_ADMIN));

    if (isOrgAdmin) {
      return Objects.equals(ownerOrganizationId, userOrganizationId);
    }

    if (subId == null || subId.isEmpty()) {
      LOGGER.warn("Ownership check failed: No subId provided for restricted/private assets");
      return false;
    }
    if (!ownerUserId.equalsIgnoreCase(subId)) {
      LOGGER.warn("Ownership check failed: User {} does not own the item", subId);
      return false;
    }
    return true;
  }

  public Future<ResponseModel> getResponseWhenResourceIsPublic(
      String accessPolicy, int totalHits, ElasticsearchResponse response) {
    LOGGER.info(
        "Ownership and access check not required for access policy " + "'{}'", accessPolicy);
    ResponseModel responseModel = new ResponseModel(List.of(response), 1, 1, totalHits);
    return Future.succeededFuture(responseModel);
  }

  public Future<ResponseModel> getResponseWhenResourceIsRestricted(
      String ownerUserId,
      String itemOrganizationId,
      GetItemRequest request,
      int totalHits,
      ElasticsearchResponse response) {
    String subId = request.getSubId();
    String did = request.getDid();
    if (subId == null || subId.isEmpty()) {
      LOGGER.warn("Restricted item access denied: Missing token (subId is null/empty)");
      return Future.failedFuture(
          new DxUnauthorizedException("Authorization token is required for restricted/pii item"));
    }

    boolean isOwner = ownerUserId.equalsIgnoreCase(request.getSubId());
    boolean isCosAdmin =
        request.getRoles() != null
            && request.getRoles().stream().anyMatch(role -> role.equalsIgnoreCase(COS_ADMIN));

    boolean isOrgAdmin =
        request.getRoles() != null
            && request.getRoles().stream().anyMatch(role -> role.equalsIgnoreCase(ORG_ADMIN))
            && Objects.equals(itemOrganizationId, request.getOrganizationId());

    boolean isAdmin = isCosAdmin || isOrgAdmin;

    setAccessFlags(response, isOwner, isAdmin);

    // Allow the owner direct access
    if (ownershipCheck(
        ownerUserId,
        request.getSubId(),
        request.getRoles(),
        itemOrganizationId,
        request.getOrganizationId())) {
      LOGGER.debug(
          "Restricted item access granted: User {} is the owner of item {}",
          subId, request.getItemId());
      ResponseModel responseModel = new ResponseModel(List.of(response), 1, 1, totalHits);
      return Future.succeededFuture(responseModel);
    }

    // Owner direct access check using delegatorId (did)
    if (did != null && did.equalsIgnoreCase(ownerUserId)) {
      LOGGER.debug("Delegator {} is the owner; direct access granted.", did);
      return succeededResponse(response, totalHits);
    }

    // Fetch apdUrl from item source
    String apdUrl = response.getSource().getString(APD_URL);
    String type = response.getSource().getJsonArray(TYPE).getString(0);
    ItemType itemType = null;
    if (type.equalsIgnoreCase(ITEM_TYPE_DATA_BANK)) {
      itemType = ItemType.DATABANK;
    } else if (type.equalsIgnoreCase(ITEM_TYPE_AI_MODEL)) {
      itemType = ItemType.AIMODEL;
    } else if (type.equalsIgnoreCase(ITEM_TYPE_APPS)) {
      itemType = ItemType.APPS;
    }
    if (apdUrl == null || apdUrl.isBlank()) {
      LOGGER.error("Restricted item missing apdUrl in metadata");
      return Future.failedFuture(new DxForbiddenException("Access denied, APD URL missing"));
    }
    ItemType finalItemType = itemType;

    // Fetch requester (sub) & delegator (did)
    Future<DxUser> subFut = keycloakUserService.getUserById(UUID.fromString(subId));
    Future<DxUser> ownerFut = keycloakUserService.getUserById(UUID.fromString(ownerUserId));

    Future<DxUser> delegatorFut =
        (did != null)
            ? keycloakUserService.getUserById(UUID.fromString(did))
            : Future.succeededFuture(null);

    return Future.all(subFut, ownerFut, delegatorFut)
        .compose(
            v -> {
              DxUser requester = subFut.result();
              DxUser owner = ownerFut.result();
              DxUser delegator = delegatorFut.result();

              Future<ResponseModel> verificationChain;

              if (did != null) {
                verificationChain =
                    policyVerifyService
                        .verify(
                            apdUrl,
                            delegator,
                            owner,
                            request.getItemId(),
                            finalItemType,
                            request.getToken())
                        .compose(dto -> completePolicySuccess(dto, response, totalHits))
                        .recover(
                            err -> {
                              LOGGER.info(
                                  "Delegator {} not authorized, trying requester {}", did, subId);
                              return policyVerifyService
                                  .verify(
                                      apdUrl,
                                      requester,
                                      owner,
                                      request.getItemId(),
                                      finalItemType,
                                      request.getToken())
                                  .compose(dto -> completePolicySuccess(dto, response, totalHits));
                            });

              } else {
                verificationChain =
                    policyVerifyService
                        .verify(
                            apdUrl,
                            requester,
                            owner,
                            request.getItemId(),
                            finalItemType,
                            request.getToken())
                        .compose(dto -> completePolicySuccess(dto, response, totalHits));
              }

              // Final fallback — ensure requester failure becomes 403
              return verificationChain.recover(
                  err -> {
                    LOGGER.info("Policy verification failed. Trying rule-based access...");

                    return accessRuleDao
                        .ruleMatches(
                            UUID.fromString(request.getItemId()),
                            requester.sub().toString(),
                            requester.organisationId(),
                            requester.roles())
                        .compose(
                            ruleMatch -> {
                              if (Boolean.TRUE.equals(ruleMatch)) {

                                LOGGER.info(
                                    "Rule-based access granted for user {}", requester.sub());

                                // fetch constraints from rule
                                return accessRuleDao
                                    .findMatchingRule(
                                        UUID.fromString(request.getItemId()),
                                        requester.sub().toString(),
                                        requester.organisationId(),
                                        requester.roles())
                                    .compose(
                                        policyObjs -> {
                                          JsonObject item = response.getSource();

                                          JsonArray policies = new JsonArray();

                                          if (policyObjs != null) {
                                            policyObjs.forEach(
                                                policy -> policies.add(policy.toJson()));
                                          }

                                          item.put(POLICIES, policies);
                                          response.setSource(item);

                                          return succeededResponse(response, totalHits);
                                        });
                              }

                              LOGGER.error("Rule-based access denied.");
                              return Future.failedFuture(
                                  new DxForbiddenException("Access denied for restricted item"));
                            });
                  });
            });
  }

  private Future<ResponseModel> completePolicySuccess(
      List<VerifyPolicyDto> policyDtos, ElasticsearchResponse response, int totalHits) {

    JsonObject item = response.getSource();
    JsonArray policies = new JsonArray();

    policyDtos.forEach(
        dto ->
            policies.add(
                new JsonObject()
                    .put(POLICY_ID, dto.getPolicyId())
                    .put(CONS, dto.getConstraints())
                    .put(EXPIRY_AT, dto.getExpiryAt())
                    .put(CREATED_AT, dto.getCreatedAt())));

    item.put(POLICIES, policies);
    response.setSource(item);

    return succeededResponse(response, totalHits);
  }

  private void setAccessFlags(
      ElasticsearchResponse response, boolean hasOwnerAccess, boolean hasAdminAccess) {

    JsonObject item = response.getSource();
    item.put("hasOwnerAccess", hasOwnerAccess);
    item.put("hasAdminAccess", hasAdminAccess);
    response.setSource(item);
  }

  // --- Helpers ---
  private Future<ResponseModel> succeededResponse(ElasticsearchResponse response, int totalHits) {
    ResponseModel responseModel = new ResponseModel(List.of(response), 1, 1, totalHits);
    return Future.succeededFuture(responseModel);
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
      queryModel =
          queryDecoder.getItemIdOrgIdQueryModel(
              patchItemRequest.getItemId(), patchItemRequest.getOrgId());
    } else if (roles.contains(PROVIDER)) {
      queryModel =
          queryDecoder.getItemIdOwnerIdQueryModel(
              patchItemRequest.getItemId(), patchItemRequest.getUserId());
    } else {
      return Future.failedFuture(new DxForbiddenException("User role not permitted to patch item"));
    }
    LOGGER.debug("query: {}", queryModel.getQueries().toElasticsearchQuery());
    String id = patchItemRequest.getItemId();
    elasticsearchService
        .getSingleDocument(docIndex, queryModel.getQueries())
        .onSuccess(
            result -> {
              if (result.getDocId() == null) {
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

                LOGGER.debug(
                    "Item with ID {} not found for update. Role: {}, message: {}",
                    id,
                    roles,
                    errorMsg);
                promise.fail(new DxNotFoundException(errorMsg));
              } else {
                LOGGER.debug("Update item with ID: {}", id);
                String docId = result.getDocId();
                LOGGER.debug("Result {}", result.getSource());
                QueryModel patchQueryModel = new QueryModel();
                patchQueryModel.createQueryModelFromDocument(patchItemRequest.getRequestBody());
                elasticsearchService
                    .patchDocument(docIndex, docId, patchQueryModel)
                    .onSuccess(
                        v -> {
                          LOGGER.debug("Item with ID {} updated successfully", id);
                          promise.complete(result);
                        })
                    .onFailure(
                        failure -> {
                          LOGGER.error(
                              "Failed to update item with ID {}: {}", id, failure.getMessage());
                          promise.fail(
                              new DxBadRequestException(
                                  "Failed to update item: " + failure.getMessage()));
                        });
              }
            })
        .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<ElasticsearchResponse> deleteItem(String id, String name) {
    LOGGER.debug("Deleting item with ID: {}", id);
    Promise<ElasticsearchResponse> promise = Promise.promise();

    if (id == null || id.isBlank()) {
      return Future.failedFuture("ID not present in request");
    }

    if (name == null || name.isBlank()) {
      return Future.failedFuture("name isn't present in request");
    }

    QueryModel boolQuery = new QueryModel(QueryType.BOOL);
    QueryModel idTermQuery = new QueryModel(QueryType.TERM);
    idTermQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));
    QueryModel nameTermQuery = new QueryModel(QueryType.TERM);
    nameTermQuery.setQueryParameters(Map.of(FIELD, NAME + KEYWORD_KEY, VALUE, name));

    boolQuery.setShouldQueries(List.of(idTermQuery, nameTermQuery));
    QueryModel queryModel = new QueryModel();
    queryModel.setQueries(boolQuery);

    elasticsearchService
        .count(docIndex, queryModel)
        .compose(
            totalHits -> {
              if (totalHits > 1) {
                LOGGER.debug("Item with ID {} has multiple associated entities", id);
                return Future.<ElasticsearchResponse>failedFuture(
                    new DxConflictException(
                        "Item has associated entities and cannot be deleted"));
              } else if (totalHits < 1) {
                LOGGER.debug("Item with ID {} not found for deletion", id);
                return Future.<ElasticsearchResponse>failedFuture(
                    new DxNotFoundException(
                        "Item not found for deletion in local catalogue"));
              }
              // Exactly 1 match -- fetch the document to get its docId
              return elasticsearchService.getSingleDocument(docIndex, boolQuery);
            })
        .onSuccess(
            result -> {
              LOGGER.debug("Item with ID {} found for deletion", id);
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
              if (getRes == null || getRes.getDocId() == null) {
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

  public Future<Boolean> exists(String itemId) {
    if (itemId == null || itemId.isBlank()) {
      return Future.failedFuture("Item ID cannot be null or empty");
    }

    QueryModel termQuery = new QueryModel(QueryType.TERM);
    termQuery.setQueryParameters(
        Map.of(
            FIELD, ID_KEYWORD,
            VALUE, itemId));

    return elasticsearchService
        .getSingleDocument(docIndex, termQuery)
        .map(res -> res.getDocId() != null)
        .recover(
            err -> {
              LOGGER.error("Local existence check failed for ID {}: {}", itemId, err.getMessage());
              return Future.failedFuture("Failed to check local catalogue existence");
            });
  }

  @Override
  public Future<Void> ownerShipTransfer(
      String oldOwnerId, String newOwnerId, String organizationId) {
    LOGGER.debug(
        "Starting ownership transfer from {} to {} in organization {}",
        oldOwnerId,
        newOwnerId,
        organizationId);

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
      LOGGER.debug(
          "Query for ownership transfer: {}", ownerShipTransferQuery.getQueries().toJson());
      return elasticsearchService
          .updateDocumentsByQuery(ownerShipTransferQuery.getQueries(), docIndex)
          .onSuccess(
              result ->
                  LOGGER.debug(
                      "Ownership transfer from {} to {} completed successfully",
                      oldOwnerId,
                      newOwnerId))
          .onFailure(
              failure -> {
                LOGGER.error(
                    "Ownership transfer from {} to {} failed: {}",
                    oldOwnerId,
                    newOwnerId,
                    failure.getMessage());
                Future.failedFuture("Ownership transfer failed: " + failure.getMessage());
              })
          .mapEmpty();

    } catch (Exception e) {
      LOGGER.error(
          "Failed to create ownership transfer query for oldOwner: {}, newOwner: {}, org: {}",
          oldOwnerId,
          newOwnerId,
          organizationId,
          e);
      return Future.failedFuture("Failed to create ownership transfer query: " + e.getMessage());
    }
  }

  private boolean isNullOrEmpty(String str) {
    return str == null || str.trim().isEmpty();
  }

  private Asset parseAndGetAsset(JsonObject result, String id) {
    LOGGER.debug("Asset info : {}", result.encodePrettily());
    try {
      String assetName = result.getString(ASSET_NAME_KEY, "").trim();
      String provider = result.getString(Constants.OWNER_ID);
      String organizationId = result.getString(ORGANIZATION_ID);
      String shortDescription = result.getString(SHORT_DESCRIPTION, "").trim();
      String organizationName = result.getString(ORGANIZATION, "");

      AssetType catAssetType = null;
      JsonArray typeArray = result.getJsonArray(Constants.TYPE);
      if (typeArray != null) {
        for (Object type : typeArray) {
          String typeStr = type.toString();
          catAssetType = AssetType.fromString(typeStr);
        }
      }

      // Validation
      if (provider == null
        || assetName.isEmpty()
        || catAssetType == null
        || organizationId == null
        || shortDescription == null) {
        LOGGER.error("Asset metadata invalid for id: {}", id);
        LOGGER.error(
          "Provider: {}, AssetName: {}, AssetType: {}, OrgId: {}, shortDescription : {}",
          provider,
          assetName,
          catAssetType,
          organizationId,
          shortDescription);
        throw new DxInternalServerErrorException("Incomplete asset metadata from catalogue");
      }

      return new Asset()
        .setItemId(id)
        .setProviderId(provider)
        .setOrganizationId(organizationId)
        .setOrganizationName(organizationName)
        .setAssetType(catAssetType.getAssetType())
        .setAssetName(assetName)
        .setShortDescription(shortDescription);

    } catch (Exception e) {
      LOGGER.error("Error building asset from catalogue metadata: {}", e.getMessage(), e);
      throw new DxInternalServerErrorException("Incomplete asset metadata from catalogue");
    }
  }


  @Override
  public Future<List<AssetRequestResponse>> enrichWithAssetInfo(List<AssetRequest> assetRequests) {

    List<Future<AssetRequestResponse>> typedFutures =
        assetRequests.stream()
            .map(
                assetRequest -> {
                  GetItemRequest itemRequest =
                      new GetItemRequest(
                          assetRequest.assetId().toString(), assetRequest.userId().toString());

                  return getItem(itemRequest)
                      .map(
                          response -> {
                            if (response == null) {
                              LOGGER.info("response is null");
                              return new AssetRequestResponse(assetRequest,null);
                            }

                            JsonObject item =
                                response.getResponse().getJsonArray("results").getJsonObject(0);

                            String itemName = item.getString("name");
                            String accessPolicy = item.getString("accessPolicy");

                            JsonArray typeArray = item.getJsonArray("type");
                            String type =
                                (typeArray != null && !typeArray.isEmpty())
                                    ? typeArray.getString(0)
                                    : null;

                            Asset asset = parseAndGetAsset(item, assetRequest.assetId().toString());


//                            LOGGER.info("Building asset response");

                            return new AssetRequestResponse(
                                assetRequest, asset);
                          })
                      .recover(
                          err ->
                              Future.succeededFuture(
                                  new AssetRequestResponse(assetRequest, null)));
                })
            .toList();

    // CompositeFuture requires List<Future>
    List<Future> futures = new ArrayList<>(typedFutures);

    return CompositeFuture.all(futures)
        .map(v -> typedFutures.stream().map(Future::result).toList());
  }

  @Override
  public Future<Void> updateEngagementCounters(UUID entityId, int likeDelta, int dislikeDelta) {
    Promise<Void> promise = Promise.promise();

    QueryModel updateQueryModel = new QueryModel();
    updateQueryModel.setScriptLanguage("painless");
    updateQueryModel.setScriptSource(
        """
    if (ctx._source.metrics == null) {
      ctx._source.metrics = [
        'likes': 0,
        'dislikes': 0,
        'views': 0,
        'downloads': 0
      ];
    }

    if (params.likeDelta != 0) {
      ctx._source.metrics.likes =
        Math.max(0, ctx._source.metrics.likes + params.likeDelta);
    }

    if (params.dislikeDelta != 0) {
      ctx._source.metrics.dislikes =
        Math.max(0, ctx._source.metrics.dislikes + params.dislikeDelta);
    }
  """);

    updateQueryModel.setScriptParams(
        Map.of(
            "likeDelta", likeDelta,
            "dislikeDelta", dislikeDelta));

    elasticsearchService
        .patchDocument(docIndex, entityId.toString(), updateQueryModel)
        .onSuccess(v -> promise.complete())
        .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<BulkSyncResult> bulkSyncMetrics(List<InteractionAggregate> aggregates) {

    List<BulkScriptUpdate> updates = new ArrayList<>();

    for (InteractionAggregate agg : aggregates) {

      LOGGER.info(
          "Syncing metrics for entityId={}, likes={}, dislikes={}",
          agg.entityId(),
          agg.likes(),
          agg.dislikes());

      updates.add(
          new BulkScriptUpdate(
              agg.entityId().toString(),
              """
              ctx._source.metrics = [
                'likes': params.likes,
                'dislikes': params.dislikes,
                'views': ctx._source.metrics?.views ?: 0,
                'downloads': ctx._source.metrics?.downloads ?: 0
              ];
              """,
              new JsonObject().put("likes", agg.likes()).put("dislikes", agg.dislikes())));
    }

    return elasticsearchService.bulkUpdateById(docIndex, updates);
  }

  @Override
  public Future<Void> updateMetric(UUID entityId, String metricField, int delta) {
    Promise<Void> promise = Promise.promise();

    QueryModel updateModel = new QueryModel();
    updateModel.setScriptLanguage("painless");
    updateModel.setScriptSource(
        """
    if (ctx._source.metrics == null) {
      ctx._source.metrics = [
        'likes': 0,
        'dislikes': 0,
        'views': 0,
        'downloads': 0
      ];
    }

    if (ctx._source.metrics[params.metricField] == null) {
      ctx._source.metrics[params.metricField] = 0;
    }

    ctx._source.metrics[params.metricField] =
      Math.max(
        0,
        ctx._source.metrics[params.metricField] + params.delta
      );
  """);

    updateModel.setScriptParams(
        Map.of(
            "metricField", metricField,
            "delta", delta));

    elasticsearchService
        .patchDocument(docIndex, entityId.toString(), updateModel)
        .onSuccess(v -> promise.complete())
        .onFailure(promise::fail);

    return promise.future();
  }

  @Override
  public Future<Boolean> isItemNameExists(String name) {

    if (name == null || name.isBlank()) {
      return Future.failedFuture("Name cannot be null or empty");
    }

    QueryModel queryModel = new QueryModel(QueryType.TERM);
    queryModel.setQueryParameters(Map.of(FIELD, "name.keyword", VALUE, name));

    return elasticsearchService
        .getSingleDocument(docIndex, queryModel)
        .map(res -> res.getDocId() != null)
        .recover(
            err -> {
              LOGGER.error(
                  "Error while checking item name existence '{}': {}", name, err.getMessage());
              return Future.failedFuture("Failed to check item name existence");
            });
  }

  /** Generates timestamp with timezone +05:30. */
  public static String getUtcDatetimeAsString() {
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    df.setTimeZone(TimeZone.getTimeZone("IST"));
    return df.format(new Date());
  }

  @Override
  public Future<Void> backupDeletedItem(Item item) {

    JsonObject backupDoc = JsonObject.mapFrom(item);

    backupDoc.put("deletedAt", getUtcDatetimeAsString()).put("originalIndex", docIndex);

    QueryModel queryModel = new QueryModel();
    queryModel.createQueryModelFromDocument(backupDoc);

    return elasticsearchService
        .createDocuments(deletedDocsIndex, List.of(queryModel))
        .mapEmpty();
  }
}
