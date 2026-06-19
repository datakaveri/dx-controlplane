package org.cdpg.dx.acl.policy.service.impl;

import static org.cdpg.dx.aaa.common.Constants.ACTIVE;
import static org.cdpg.dx.aaa.common.Constants.DETAIL;
import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_APPS;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_DATA_BANK;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_RESOURCE_GROUP;
import static org.cdpg.dx.aaa.common.Constants.NAME;
import static org.cdpg.dx.aaa.common.Constants.ORGANIZATION_ID;
import static org.cdpg.dx.aaa.common.Constants.PROVIDER_USER_ID;
import static org.cdpg.dx.aaa.common.Constants.TITLE;
import static org.cdpg.dx.aaa.common.Constants.TYPE;
import static org.cdpg.dx.acl.accessRequest.config.Constants.CONSUMER_EMAIL_ID;
import static org.cdpg.dx.acl.accessRequest.config.Constants.CONSUMER_LAST_NAME;
import static org.cdpg.dx.acl.accessRequest.config.Constants.OWNER_EMAIL_ID;
import static org.cdpg.dx.acl.accessRequest.config.Constants.OWNER_FIRST_NAME;
import static org.cdpg.dx.acl.accessRequest.config.Constants.OWNER_LAST_NAME;
import static org.cdpg.dx.acl.accessRequest.config.Constants.USER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONSTRAINTS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONSUMER_FIRST_NAME;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONSUMER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ASSET_ORGANIZATION_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CONSTRAINTS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CONSUMER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_EXPIRY_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ITEM_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_OWNER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.EMAIL;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.FIRST_NAME;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ITEM_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.LAST_NAME;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ORGANIZATION;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.OWNER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.POLICY_ID;
import static org.cdpg.dx.auth.model.DxRole.CONSUMER;
import static org.cdpg.dx.auth.model.DxRole.PROVIDER;
import static org.cdpg.dx.catalogueService.config.Constants.ASSET_NAME_KEY;
import static org.cdpg.dx.catalogueService.config.Constants.SHORT_DESCRIPTION;
import static org.cdpg.dx.common.HttpStatusCode.BAD_REQUEST;
import static org.cdpg.dx.common.HttpStatusCode.CONFLICT;
import static org.cdpg.dx.common.HttpStatusCode.FORBIDDEN;
import static org.cdpg.dx.common.HttpStatusCode.INTERNAL_SERVER_ERROR;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.acl.accessRequest.dao.config.DbConstants;
import org.cdpg.dx.acl.accessRequest.dao.model.AssetType;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.dao.model.VerifyPolicyDto;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.acl.rule.dao.AccessRuleDao;
import org.cdpg.dx.catalogueService.config.Constants;
import org.cdpg.dx.catalogueService.models.Asset;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.ResponseUrn;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.ResourceObj;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class PolicyServiceImpl implements PolicyService {
  private static final Logger LOGGER = LogManager.getLogger(PolicyServiceImpl.class);
  private static final String FAILURE_MESSAGE = "Policy could not be deleted";
  private final ItemService itemService;
  private final KeycloakUserService keycloakUserService;
  private final PolicyDao policyDao;
  private final AccessRuleDao accessRuleDao;
  private final String apdUrl;

  public PolicyServiceImpl(
      ItemService itemService,
      KeycloakUserService keycloakUserService,
      PolicyDao policyDao,
      AccessRuleDao accessRuleDao,
      String apdUrl) {
    this.itemService = itemService;
    this.keycloakUserService = keycloakUserService;
    this.accessRuleDao = accessRuleDao;
    this.apdUrl = apdUrl;
    this.policyDao = policyDao;
  }

  @Override
  public Future<Void> createPolicy(List<CreatePolicyRequest> requests, DxUser caller) {
    // ownership checks -- caller must be owner or org delegate; simplified here
    UUID userId = caller.sub();

    Set<UUID> itemIds =
        requests.stream().map(CreatePolicyRequest::getItemId).collect(Collectors.toSet());
    Set<String> itemTypes =
        requests.stream().map(req -> req.getItemType().getTypeValue()).collect(Collectors.toSet());

    // Fail fast if resource_group
    if (itemTypes.contains(ITEM_TYPE_RESOURCE_GROUP)) {
      LOGGER.debug("Contains resource group");
      return Future.failedFuture(
          generateErrorResponse(BAD_REQUEST, "Policy creation for resource group is restricted"));
    }

    LOGGER.debug("itemIds: {}", itemIds);
    LOGGER.debug("itemTypes: {}", itemTypes);

    // Ownership check
    return checkForItemsInDb(itemIds, itemTypes, caller)
        .compose(
            resourceObjs -> {
              Set<UUID> providerIds =
                  resourceObjs.stream().map(ResourceObj::getProviderId).collect(Collectors.toSet());

              // Build map: itemId -> organizationId
              Map<UUID, UUID> itemOrgMap =
                  resourceObjs.stream()
                      .collect(
                          Collectors.toMap(ResourceObj::getItemId, ResourceObj::getOrganizationId));

              // Create lookup map
              Map<UUID, ResourceObj> resourceMap =
                  resourceObjs.stream()
                      .collect(Collectors.toMap(ResourceObj::getItemId, Function.identity()));

              // Enrich requests
              requests.forEach(
                  req -> {
                    UUID orgId = itemOrgMap.get(req.getItemId());
                    if (orgId != null) {
                      req.setItemOrganizationId(orgId.toString());
                    }
                    if (!itemOrgMap.containsKey(req.getItemId())) {
                      throw new IllegalStateException("Missing orgId for item: " + req.getItemId());
                    }

                    // Access type validation
                    ResourceObj resourceObj = resourceMap.get(req.getItemId());

                    Set<String> allowedAccessTypes =
                        getAllowedAccessTypes(resourceObj.getResourceServers());

                    validateAccessConstraints(req.getConstraints(), allowedAccessTypes);
                  });

              boolean isOwner = providerIds.stream().allMatch(id -> id.equals(userId));

              boolean isOrgAdmin = caller.roles().contains("org_admin");

              if (isOwner) {
                return policyDao.checkExistingPoliciesForIds(requests, userId);
              }

              if (isOrgAdmin) {
                return validateOrgAdminAccess(resourceObjs, caller)
                    .compose(v -> policyDao.checkExistingPoliciesForIds(requests, userId));
              }

              return Future.failedFuture(
                  generateErrorResponse(
                      FORBIDDEN, "Access Denied: Not owner or org_admin of same organisation"));
            })
        .compose(
            queryResult -> {
              JsonArray existingPolicies = queryResult.getRows();

              if (existingPolicies != null && !existingPolicies.isEmpty()) {

                Set<String> conflictingAccessTypes = new HashSet<>();

                for (CreatePolicyRequest request : requests) {

                  Set<String> requestedTypes = extractAccessTypes(request.getConstraints());

                  for (Object obj : existingPolicies) {

                    JsonObject row = (JsonObject) obj;

                    JsonObject existingConstraints =
                        row.getJsonObject(DB_CONSTRAINTS, new JsonObject());

                    Set<String> existingTypes = extractAccessTypes(existingConstraints);

                    existingTypes.retainAll(requestedTypes);

                    conflictingAccessTypes.addAll(existingTypes);
                  }
                }

                if (!conflictingAccessTypes.isEmpty()) {

                  return Future.failedFuture(
                      generateErrorResponse(
                          CONFLICT,
                          "Active policy already exists for accessType(s): "
                              + String.join(", ", conflictingAccessTypes)));
                }
              }

              return policyDao.insertPolicies(requests, userId);
            })
        .onSuccess(
            rowList -> {
              JsonArray response = createResponseArray(rowList);
              LOGGER.debug("Policy created successfully with info: {}", response);
            })
        .onFailure(err -> LOGGER.error("Failed to create policy: {}", err.getMessage()))
        .compose(
            insertResults -> {
              List<Future> ruleFutures = new ArrayList<>();

              for (int i = 0; i < insertResults.size(); i++) {

                QueryResult result = insertResults.get(i);
                JsonObject row = result.getRows().getJsonObject(0);

                UUID policyId = UUID.fromString(row.getString("_id"));
                CreatePolicyRequest req = requests.get(i);

                JsonObject constraints = req.getConstraints();
                String expiryAt = String.valueOf(req.getExpiryTime());

                if (constraints != null && constraints.containsKey("subjects")) {

                  JsonObject subjects = constraints.getJsonObject("subjects");

                  ruleFutures.add(
                      accessRuleDao.createRule(
                          policyId, req.getItemId(), userId, subjects, constraints, expiryAt));
                }
              }

              return CompositeFuture.all(ruleFutures).mapEmpty();
            });
  }

  private Set<String> getAllowedAccessTypes(JsonArray resourceServers) {
    Set<String> set = new HashSet<>();

    for (int i = 0; i < resourceServers.size(); i++) {
      JsonObject rs = resourceServers.getJsonObject(i);

      JsonArray accessTypes = rs.getJsonArray("accessTypes", new JsonArray());
      for (int j = 0; j < accessTypes.size(); j++) {
        set.add(accessTypes.getString(j));
      }
    }

    return set; // Example: ["api", "sub", "file", "async"]
  }

  private void validateAccessConstraints(JsonObject constraints, Set<String> allowedAccessTypes) {

    if (constraints == null || !constraints.containsKey("access")) {
      return;
    }

    JsonArray requested = constraints.getJsonArray("access", new JsonArray());

    for (int i = 0; i < requested.size(); i++) {

      JsonObject accessObj = requested.getJsonObject(i);
      String type = accessObj.getString("accessType");

      if (!allowedAccessTypes.contains(type)) {
        throw new DxValidationException(
            generateErrorResponse(
                BAD_REQUEST,
                "Requested access type '" + type + "' is not allowed for this resource"));
      }
    }
  }

  private Set<String> extractAccessTypes(JsonObject constraints) {

    Set<String> accessTypes = new HashSet<>();

    if (constraints == null) {
      return accessTypes;
    }

    JsonArray access = constraints.getJsonArray("access", new JsonArray());

    for (int i = 0; i < access.size(); i++) {

      JsonObject obj = access.getJsonObject(i);

      String type = obj.getString("accessType");

      if (type != null) {
        accessTypes.add(type);
      }
    }

    return accessTypes;
  }

  private Future<Void> validateOrgAdminAccess(List<ResourceObj> resources, DxUser caller) {

    String callerOrgId = caller.organisationId();

    for (ResourceObj resource : resources) {

      UUID resourceOrgId = resource.getOrganizationId();

      // If item has NO orgId → org admin cannot manage it
      if (resourceOrgId == null) {
        return Future.failedFuture(
            generateErrorResponse(
                FORBIDDEN, "Org Admin cannot create policy for items without organisation"));
      }

      // Org mismatch
      if (!resourceOrgId.toString().equals(callerOrgId)) {
        return Future.failedFuture(
            generateErrorResponse(
                FORBIDDEN,
                "Org Admin cannot create policy for resources outside their organisation"));
      }
    }

    return Future.succeededFuture();
  }

  public Future<List<ResourceObj>> checkForItemsInDb(
      Set<UUID> itemIdList, Set<String> itemTypeRequest, DxUser user) {

    if (itemIdList.isEmpty()) {
      LOGGER.warn("item id list is empty...");
      return Future.succeededFuture(List.of());
    }

    // Fetch items from catalogue directly
    return fetchResourcesFromCatalogue(itemIdList, user.sub().toString())
        .compose(
            resourceObjs -> {
              Set<UUID> providerIdSet = new HashSet<>();
              Set<UUID> organizationIdSet = new HashSet<>();
              List<ResourceObj> resourceObjList = new ArrayList<>();

              for (ResourceObj obj : resourceObjs) {
                // Validate item types
                if (!itemTypeRequest.contains(obj.getItemType().name())) {
                  return Future.failedFuture(
                      generateErrorResponse(
                          BAD_REQUEST, "Invalid item type for ID: " + obj.getItemId()));
                }

                // Optionally: validate user has access via resource server URLs
                // if (obj.getResourceServerUrls().stream().noneMatch(url ->
                // url.equals(user.getResourceServerUrls()))) {
                //     return Future.failedFuture(generateErrorResponse(FORBIDDEN,
                //             "Access denied: user does not have rights for resource ID " +
                // obj.getItemId()));
                // }

                providerIdSet.add(obj.getProviderId());
                organizationIdSet.add(obj.getOrganizationId());
                resourceObjList.add(obj);
              }

              return Future.succeededFuture(resourceObjList);
            })
        .recover(
            failure -> {
              String failureMessage = failure.getMessage();
              if (failureMessage.contains(TYPE) && failureMessage.contains(TITLE)) {
                return Future.failedFuture(failureMessage);
              } else {
                return Future.failedFuture(generateErrorResponse(BAD_REQUEST, failureMessage));
              }
            });
  }

  public Future<List<ResourceObj>> fetchResourcesFromCatalogue(Set<UUID> ids, String userId) {
    List<Future> futures =
        ids.stream()
            .map(id -> fetchAndValidateResource(id, userId)) // fetch each UUID
            .collect(Collectors.toList());

    // Combine all futures
    return CompositeFuture.all(futures)
        .map(
            composite ->
                futures.stream()
                    .map(f -> ((Future<ResourceObj>) f).result())
                    .collect(Collectors.toList()));
  }

  /** Fetch a single resource using ItemService and apply all catalogue validations */
  private Future<ResourceObj> fetchAndValidateResource(UUID id, String userId) {
    Promise<ResourceObj> promise = Promise.promise();

    GetItemRequest request = new GetItemRequest(id.toString(), userId);
    itemService
        .getItem(request)
        .onFailure(
            ar -> {
              LOGGER.error("fetchItem error", ar);

              if (ar.getCause() != null) {
                LOGGER.error("Cause : {}", ar.getCause().getLocalizedMessage());
              }

              LOGGER.error("Message : {}", ar.getMessage());

              promise.fail(INTERNAL_SERVER_ERROR.getDescription());
            })
        .onSuccess(
            catSuccessResponse -> {
              // Filter out null Elasticsearch responses
              List<JsonObject> validResponses =
                  catSuccessResponse.getElasticsearchResponses().stream()
                      .filter(Objects::nonNull)
                      .toList();

              if (!validResponses.isEmpty()) {
                JsonObject resultJson = validResponses.getFirst();
                LOGGER.info(resultJson.encodePrettily());
                List<String> resServerUrls = null;
                String apdUrlOfResource = "";

                // Validate type
                String type = resultJson.getJsonArray(TYPE).getString(0);
                String idFromResponse = resultJson.getString(ID);
                /* check if the id being sent is of valid type*/
                if (!ITEM_TYPE_DATA_BANK.equalsIgnoreCase(type)
                    && !ITEM_TYPE_AI_MODEL.equalsIgnoreCase(type)
                    && !ITEM_TYPE_APPS.equalsIgnoreCase(type)) {
                  LOGGER.error("Invalid item type: {}", type);
                  promise.fail(
                      generateFailureMessage(
                          BAD_REQUEST,
                          ResponseUrn.BAD_REQUEST_URN,
                          "Given id is invalid - only DataBank or AiModel items are supported, but "
                              + "got: "
                              + type));
                  return;
                }

                // Extract provider
                UUID provider = UUID.fromString(resultJson.getString(PROVIDER_USER_ID));
                if (provider == null) {
                  promise.fail(
                      generateFailureMessage(
                          INTERNAL_SERVER_ERROR,
                          ResponseUrn.INTERNAL_SERVER_ERROR,
                          "Provider ID missing in catalogue response"));
                  return;
                }

                // Extract resource servers
                JsonArray resourceServers =
                    resultJson.getJsonArray("resourceServer", new JsonArray());

                resServerUrls =
                    resourceServers.stream()
                        .map(obj -> ((JsonObject) obj).getString("url"))
                        .filter(Objects::nonNull)
                        .toList();
                if (resServerUrls.isEmpty()) {
                  promise.fail(
                      generateFailureMessage(
                          INTERNAL_SERVER_ERROR,
                          ResponseUrn.INTERNAL_SERVER_ERROR,
                          "Resource server URLs missing in catalogue response"));
                  return;
                }
                apdUrlOfResource = resultJson.getString(APD_URL);
                if (!apdUrl.equals(apdUrlOfResource)) {
                  /* if the resource has an APD URL that is not equal to the current APD URL*/
                  String failureMessage =
                      generateFailureMessage(
                          FORBIDDEN,
                          ResponseUrn.FORBIDDEN_URN,
                          "Resource is forbidden to access, as the APD URL for the resource : "
                              + apdUrlOfResource
                              + " is different than the current APD : "
                              + apdUrl);
                  promise.fail(failureMessage);
                } else {
                  ItemType itemType = null;
                  if (type.equalsIgnoreCase(ITEM_TYPE_DATA_BANK)) {
                    itemType = ItemType.DATABANK;
                  } else if (type.equalsIgnoreCase(ITEM_TYPE_AI_MODEL)) {
                    itemType = ItemType.AIMODEL;
                  } else if (type.equalsIgnoreCase(ITEM_TYPE_APPS)) {
                    itemType = ItemType.APPS;
                  }
                  ResourceObj resourceObj =
                      new ResourceObj(id, provider, resServerUrls, resourceServers, itemType);
                  if (resultJson.getString(ORGANIZATION_ID) != null) {
                    resourceObj.setOrganizationId(
                        UUID.fromString(resultJson.getString(ORGANIZATION_ID)));
                  }
                  promise.complete(resourceObj);
                }
              } else {
                String message = "Item not found for ID: " + id;
                LOGGER.error(message);
                promise.fail(new DxForbiddenException(message));
              }
            });

    return promise.future();
  }

  /** Generate failure JSON string (same as CatalogueClient) */
  private String generateFailureMessage(
      HttpStatusCode httpStatusCode, ResponseUrn responseUrn, String detail) {
    return new JsonObject()
        .put(TYPE, httpStatusCode.getValue())
        .put(TITLE, responseUrn.getUrn())
        .put(DETAIL, detail)
        .encode();
  }

  @Override
  public Future<List<PolicyDto>> getPolicy(DxUser user) {
    List<String> role = user.roles();
    Future<QueryResult> daoFuture;

    // TODO: Fetch rsurls from user token if needed as an extension
    //    List<Object> resourceServerUrls = new ArrayList<>();
    //    resourceServerUrls.add("rs.forestdx.iudx.io");
    //    resourceServerUrls.add("file.forestdx.iudx.io");
    if (role.contains(PROVIDER.value()) || role.contains("providerDelegate")) {
      daoFuture = policyDao.getPoliciesByProvider(user.sub().toString());
    } else if (role.contains(CONSUMER.value()) || role.contains("consumerDelegate")) {
      daoFuture = policyDao.getPoliciesByConsumer(user.email());
    } else {
      JsonObject error =
          new JsonObject()
              .put(TYPE, HttpStatusCode.BAD_REQUEST.getValue())
              .put(TITLE, ResponseUrn.BAD_REQUEST_URN.getUrn())
              .put(DETAIL, "Invalid role");
      return Future.failedFuture(error.encode());
    }

    return daoFuture.map(
        queryResult -> {
          JsonArray rows = queryResult.getRows();

          if (rows.isEmpty()) {
            JsonObject error =
                new JsonObject()
                    .put(TYPE, HttpStatusCode.NOT_FOUND.getValue())
                    .put(TITLE, ResponseUrn.RESOURCE_NOT_FOUND_URN.getUrn())
                    .put(DETAIL, "Policy not found");
            throw new RuntimeException(error.encode());
          }

          return rows.stream()
              .map(
                  obj -> {
                    JsonObject row = (JsonObject) obj;

                    // Log each row from DB
                    LOGGER.info("Policy Row: {}", row.encodePrettily());

                    // Enrich row before passing to DTO
                    if (role.contains(PROVIDER.value()) || role.contains("providerDelegate")) {
                      row.mergeIn(getProviderInfo(row));
                    } else {
                      row.mergeIn(getConsumerInfo(row));
                    }

                    return new PolicyDto(row);
                  })
              .collect(Collectors.toList());
        });
  }

  private JsonObject getConsumerInfo(JsonObject row) {
    return new JsonObject()
        .put(
            DbConstants.CONSUMER,
            new JsonObject()
                .put(ID, row.getString(CONSUMER_ID))
                .put(EMAIL, row.getString(CONSUMER_EMAIL_ID))
                .put(
                    NAME,
                    new JsonObject()
                        .put(FIRST_NAME, row.getString(CONSUMER_FIRST_NAME))
                        .put(LAST_NAME, row.getString(CONSUMER_LAST_NAME))));
  }

  private JsonObject getProviderInfo(JsonObject row) {
    return new JsonObject()
        .put(
            DbConstants.PROVIDER,
            new JsonObject()
                .put(ID, row.getString(OWNER_ID))
                .put(EMAIL, row.getString(OWNER_EMAIL_ID))
                .put(
                    NAME,
                    new JsonObject()
                        .put(FIRST_NAME, row.getString(OWNER_FIRST_NAME))
                        .put(LAST_NAME, row.getString(OWNER_LAST_NAME))));
  }

  @Override
  public Future<Void> deActivatePolicy(String policyId, DxUser user) {

    return policyDao
        .verifyPolicy(UUID.fromString(policyId))
        .compose(
            result -> {
              if (result.getRows().isEmpty()) {
                return Future.failedFuture(
                    new JsonObject()
                        .put(TYPE, HttpStatusCode.NOT_FOUND.getValue())
                        .put(TITLE, ResponseUrn.RESOURCE_NOT_FOUND_URN.getUrn())
                        .put(DETAIL, FAILURE_MESSAGE + ", as it doesn't exist")
                        .encode());
              }
              JsonObject row = result.getRows().getJsonObject(0);
              String ownerId = row.getString(DB_OWNER_ID);
              String status = row.getString(DB_STATUS);
              String assetOrgId = row.getString(DB_ASSET_ORGANIZATION_ID);
              boolean isOwner = ownerId.equals(user.sub().toString());
              boolean isOrgAdmin =
                  user.roles().contains("org_admin") && assetOrgId.equals(user.organisationId());

              LOGGER.debug(
                  "ownerId={}, status={}, assetOrgId={}, userId={}, userOrgId={}, roles={}, isOwner={}, isOrgAdmin={}",
                  ownerId,
                  status,
                  assetOrgId,
                  user.sub(),
                  user.organisationId(),
                  user.roles(),
                  isOwner,
                  isOrgAdmin);
              if (!isOwner && !isOrgAdmin) {
                LOGGER.error("Failure : policy does not belong to the user or organisation");
                return Future.failedFuture(
                    new JsonObject()
                        .put(TYPE, HttpStatusCode.FORBIDDEN.getValue())
                        .put(TITLE, ResponseUrn.FORBIDDEN_URN.getUrn())
                        .put(
                            DETAIL,
                            FAILURE_MESSAGE
                                + ", as policy doesn't belong to the user or organisation")
                        .encode());
              }

              if (!ACTIVE.equalsIgnoreCase(status)) {
                LOGGER.error("Failure : policy is not active");
                return Future.failedFuture(
                    getFailureResponse(
                        new JsonObject(), FAILURE_MESSAGE + ", as policy is not ACTIVE"));
              }

              // Passed all checks → proceed to delete
              Promise<Void> promise = Promise.promise();
              policyDao
                  .deActivatePolicy(UUID.fromString(policyId))
                  .onFailure(
                      err -> {
                        LOGGER.debug("query failed: {}", err.getLocalizedMessage());
                        promise.fail(
                            getFailureResponse(
                                new JsonObject(), FAILURE_MESSAGE + ", update query failed"));
                      })
                  .onSuccess(
                      delResult -> {
                        if (delResult.getRows().isEmpty()) {
                          promise.fail(
                              getFailureResponse(
                                  new JsonObject(), FAILURE_MESSAGE + " , as policy is expired"));
                        } else {
                          LOGGER.info("query succeeded");
                          JsonObject responseJson = delResult.getRows().getJsonObject(0);
                          LOGGER.debug("Delete policy succeeded: {}", responseJson);
                          promise.complete();
                        }
                      });
              return deactivatePolicyLifecycle(UUID.fromString(policyId));
            });
  }

  private Future<Void> deactivatePolicyLifecycle(UUID policyId) {

    return accessRuleDao.updateStatusByPolicyId(policyId, "INACTIVE").mapEmpty();
  }

  /** Verify if an ACTIVE policy exists for a given user/item pair. */
  @Override
  public Future<List<VerifyPolicyDto>> initiateVerifyPolicy(
      UUID ownerId, String userId, UUID itemId, ItemType itemType, DxUser user) {

    Future<List<VerifyPolicyDto>> policiesFuture =
        policyDao
            .checkExistingPoliciesForIds(itemId, ownerId, userId)
            .map(
                queryResult -> {
                  JsonArray rows = queryResult.getRows();

                  List<VerifyPolicyDto> policies = new ArrayList<>();

                  if (rows != null) {
                    for (int i = 0; i < rows.size(); i++) {
                      JsonObject row = rows.getJsonObject(i);

                      policies.add(
                          new VerifyPolicyDto(
                              row.getString(DB_ID),
                              ResponseUrn.VERIFY_SUCCESS_URN.getUrn(),
                              row.getJsonObject(CONSTRAINTS),
                              row.getString(DB_EXPIRY_AT)));
                    }
                  }

                  return policies;
                });

    Future<PolicyDto> ruleFuture =
        keycloakUserService
            .getUserById(UUID.fromString(userId))
            .compose(
                fullUser ->
                    accessRuleDao.findMatchingRule(
                        itemId,
                        fullUser.sub().toString(),
                        fullUser.organisationId(),
                        fullUser.roles()))
            .otherwiseEmpty();

    return Future.all(policiesFuture, ruleFuture)
        .compose(
            composite -> {
              List<VerifyPolicyDto> policies = composite.resultAt(0);
              PolicyDto rule = composite.resultAt(1);

              if (rule != null && !rule.toJson().isEmpty()) {
                policies.add(
                    new VerifyPolicyDto(
                        rule.getPolicyId(),
                        ResponseUrn.VERIFY_SUCCESS_URN.getUrn(),
                        rule.getConstraints(),
                        rule.getExpiryAt().toString()));
              }

              if (policies.isEmpty()) {
                return Future.failedFuture(
                    generateErrorResponse(
                        HttpStatusCode.FORBIDDEN, "No ACTIVE policy exists for this user/item"));
              }

              return Future.succeededFuture(policies);
            })
        .recover(
            err -> {
              LOGGER.error("Error during initiateVerifyPolicy: {}", err.getMessage());
              return Future.failedFuture(
                  generateErrorResponse(INTERNAL_SERVER_ERROR, err.getMessage()));
            });
  }

  @Override
  public Future<PaginatedResult<PolicyDto>> listPolicies(PaginatedRequest request) {
    return policyDao.getAllWithFilters(request);
  }

  @Override
  public Future<PaginatedResult<PolicyDto>> listPolicies(
      PaginatedRequest request, Set<String> policyIds, String consumerId) {

    return policyDao.getPoliciesWithAccessControl(request, policyIds, consumerId);
  }

  @Override
  public Future<PaginatedResult<PolicyDto>> enrichPolicyRequestsWithItemDetails(
      PaginatedResult<PolicyDto> pagedResult) {

    List<Future<?>> futures = new ArrayList<>();

    for (PolicyDto dto : pagedResult.data()) {

      if (dto.getItemId() == null) continue;

      GetItemRequest request = new GetItemRequest(dto.getItemId(), "");

      Future<Void> future =
          itemService
              .getItem(request)
              .onSuccess(
                  response -> {
                    if (!response.getElasticsearchResponses().isEmpty()) {

                      JsonObject itemJson = response.getElasticsearchResponses().getFirst();
                      Asset asset = parseAndGetAsset(itemJson, dto.getItemId());

                      // Override DB values with catalogue values
                      dto.setAssetName(asset.getAssetName());
                      dto.setAssetType(asset.getAssetType());
                      dto.setShortDescription(asset.getShortDescription());
                      dto.setItemOrganizationId(asset.getOrganizationId());
                      dto.setItemOrganizationName(asset.getOrganizationName());
                    }
                  })
              .onFailure(
                  err -> {
                    LOGGER.warn("Failed to fetch item {}: {}", dto.getItemId(), err.getMessage());
                    // fallback: keep DB values
                  })
              .mapEmpty();

      futures.add(future);
    }

    return Future.all(futures).map(v -> pagedResult);
  }

  @Override
  public Future<PaginatedResult<PolicyDto>> enrichPolicyRequestsWithUserInfo(
      PaginatedResult<PolicyDto> pagedResult) {

    List<Future<?>> futures = new ArrayList<>();

    for (PolicyDto dto : pagedResult.data()) {

      // Consumer enrichment
      if (dto.getConsumerId() != null) {
        Future<Void> consumerFuture =
            keycloakUserService
                .getUserById(UUID.fromString(dto.getConsumerId()))
                .onSuccess(
                    user -> {
                      dto.setConsumerEmail(user.email());
                      dto.setConsumerFirstName(user.givenName());
                      dto.setConsumerLastName(user.familyName());
                      dto.setConsumerOrganization(user.organisationName());
                    })
                .recover(
                    err -> {
                      LOGGER.warn(
                          "Failed to fetch consumer {}: {}", dto.getConsumerId(), err.getMessage());

                      dto.setConsumerEmail(null);
                      dto.setConsumerFirstName(null);
                      dto.setConsumerLastName(null);
                      dto.setConsumerOrganization(null);

                      return Future.succeededFuture();
                    })
                .mapEmpty();

        futures.add(consumerFuture);
      }

      // Owner enrichment
      if (dto.getOwnerId() != null) {
        Future<Void> ownerFuture =
            keycloakUserService
                .getUserById(UUID.fromString(dto.getOwnerId()))
                .onSuccess(
                    user -> {
                      dto.setOwnerEmail(user.email());
                      dto.setOwnerFirstName(user.givenName());
                      dto.setOwnerLastName(user.familyName());
                      dto.setOwnerOrganization(user.organisationName());
                    })
                .recover(
                    err -> {
                      LOGGER.warn(
                          "Failed to fetch owner {}: {}", dto.getOwnerId(), err.getMessage());

                      dto.setOwnerEmail(null);
                      dto.setOwnerFirstName(null);
                      dto.setOwnerLastName(null);
                      dto.setOwnerOrganization(null);

                      return Future.succeededFuture();
                    })
                .mapEmpty();

        futures.add(ownerFuture);
      }
    }

    return Future.all(futures).map(v -> pagedResult);
  }

  private Asset parseAndGetAsset(JsonObject result, String id) {
    LOGGER.debug("Asset info : {}", result.encodePrettily());
    try {
      String assetName = result.getString(ASSET_NAME_KEY, "").trim();
      String provider = result.getString(Constants.OWNER_ID);
      String organizationId = result.getString(Constants.ORGANIZATION_ID);
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

  private String generateErrorResponse(HttpStatusCode httpStatusCode, String errorMessage) {
    return new JsonObject()
        .put(TYPE, httpStatusCode.getValue())
        .put(TITLE, httpStatusCode.getPath())
        .put(DETAIL, errorMessage)
        .encode();
  }

  private JsonArray createResponseArray(List<QueryResult> queryResults) {
    JsonArray response = new JsonArray();
    final JsonObject[] ownerJsonObject = {null};

    for (QueryResult queryResult : queryResults) {
      for (int i = 0; i < queryResult.getRows().size(); i++) {
        JsonObject row = queryResult.getRows().getJsonObject(i);

        JsonObject jsonObject =
            new JsonObject()
                .put(POLICY_ID, row.getString(DB_ID))
                .put(USER_ID, row.getString(DB_CONSUMER_ID))
                .put(ITEM_ID, row.getString(DB_ITEM_ID))
                .put(DB_EXPIRY_AT, row.getString(DB_EXPIRY_AT));

        if (ownerJsonObject[0] == null) {
          ownerJsonObject[0] = new JsonObject().put(OWNER_ID, row.getValue(DB_OWNER_ID).toString());
        }
        response.add(jsonObject);
      }
    }

    response.add(ownerJsonObject[0]);
    return response;
  }

  private String getFailureResponse(JsonObject response, String detail) {
    return response
        .put(TYPE, BAD_REQUEST.getValue())
        .put(TITLE, BAD_REQUEST.getPath())
        .put(DETAIL, detail)
        .encode();
  }
}
