package org.cdpg.dx.acl.accessRequest.service.impl;

import static org.cdpg.dx.aaa.common.Constants.IN_ACTIVE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_EXPIRY_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.EXPIRY_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ORGANIZATION;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.POLICY_ID;
import static org.cdpg.dx.catalogueService.config.Constants.ASSET_NAME_KEY;
import static org.cdpg.dx.catalogueService.config.Constants.ORGANIZATION_ID;
import static org.cdpg.dx.catalogueService.config.Constants.OWNER_ID;
import static org.cdpg.dx.catalogueService.config.Constants.SHORT_DESCRIPTION;
import static org.cdpg.dx.catalogueService.config.Constants.TYPE;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.dao.model.AssetType;
import org.cdpg.dx.acl.accessRequest.dao.model.HasAccessResponse;
import org.cdpg.dx.acl.accessRequest.dao.model.PolicyAccessInfo;
import org.cdpg.dx.acl.accessRequest.dao.model.Status;
import org.cdpg.dx.acl.accessRequest.service.AccessRequestService;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.acl.rule.dao.AccessRuleDao;
import org.cdpg.dx.catalogueService.models.Asset;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxCreateAccessRequestForbiddenException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxForbiddenNoAccessException;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.common.model.RequestType;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class AccessRequestServiceImpl implements AccessRequestService {

  private static final Logger LOGGER = LogManager.getLogger(AccessRequestServiceImpl.class);

  private final AccessRequestDao accessRequestDao;
  private final ItemService itemService;
  private final PolicyDao policyDao;
  private final AccessRuleDao accessRuleDao;
  private final KeycloakUserService keycloakUserService;

  public AccessRequestServiceImpl(
      KeycloakUserService keycloakUserService,
      ItemService itemService,
      AccessRequestDao accessRequestDao,
      PolicyDao policyDao,
      AccessRuleDao accessRuleDao) {
    this.keycloakUserService = keycloakUserService;
    this.itemService = itemService;
    this.accessRequestDao = Objects.requireNonNull(accessRequestDao);
    this.policyDao = policyDao;
    this.accessRuleDao = accessRuleDao;
  }

  @Override
  public Future<AccessRequestDto> createAccessRequest(
      UUID consumerId,
      UUID itemId,
      RequestType requestType,
      JsonObject additionalInfo,
      JsonObject constraints) {

    return keycloakUserService
        .getUserById(consumerId)
        .compose(
            fullUser -> {
              AccessRequestDto accessRequestDto =
                  new AccessRequestDto()
                      .setRequestType(requestType)
                      .setConsumerId(fullUser.sub().toString())
                      .setConsumerEmail(fullUser.email())
                      .setConsumerFirstName(fullUser.givenName())
                      .setConsumerLastName(fullUser.familyName())
                      .setConsumerOrganization(fullUser.organisationName());

              if (additionalInfo != null) {
                accessRequestDto.setAdditionalInfo(additionalInfo);
              }
              if (constraints != null) {
                accessRequestDto.setConstraints(constraints);
              }

              return accessRequestDao
                  .isAccessRequestPresent(fullUser.sub(), itemId)
                  .compose(
                      isPresent -> {
                        if (isPresent) {
                          String message = "Access request already exists";
                          LOGGER.warn(message);
                          return Future.failedFuture(
                              new DxConflictException("Access request already exists"));
                        }

                        GetItemRequest request = new GetItemRequest(itemId.toString(), "");

                        return itemService.getItem(request);
                      })
                  .compose(
                      responseModel -> {
                        if (responseModel.getElasticsearchResponses().isEmpty()) {
                          return Future.failedFuture(
                              new DxForbiddenException("Item not found for ID: " + itemId));
                        }

                        JsonObject itemJson = responseModel.getElasticsearchResponses().getFirst();

                        Asset asset = parseAndGetAsset(itemJson, itemId.toString());

                        // Provider cannot request own resource
                        if (asset.getProviderId().equals(fullUser.sub().toString())) {
                          String message =
                              "Provider cannot create an access request for their own resource";
                          LOGGER.warn(message);
                          return Future.failedFuture(
                              new DxCreateAccessRequestForbiddenException(message));
                        }

                        accessRequestDto
                            .setAssetType(asset.getAssetType())
                            .setAssetName(asset.getAssetName())
                            .setProviderId(asset.getProviderId())
                            .setItemOrganizationId(asset.getOrganizationId())
                            .setShortDescription(asset.getShortDescription())
                            .setItemId(asset.getItemId());

                        return accessRequestDao.create(accessRequestDto);
                      });
            })
        .onSuccess(dto -> LOGGER.info("Access request created successfully: {}", dto))
        .onFailure(err -> LOGGER.error("Failed to create access request", err));
  }

  @Override
  public Future<AccessRequestDto> approveAccessRequest(
      UUID providerId,
      UUID requestId,
      LocalDateTime expiryAt,
      UUID providerOrganizationId,
      boolean isUserOrgAdmin,
      JsonObject constraints,
      String providerComment,
      String feedbackToConsumer) {
    if (providerOrganizationId == null) {
      LOGGER.error("Provider organization ID is null for requestId: {}", requestId);
      return Future.failedFuture(
          new DxForbiddenException("Provider organization ID in the token, cannot be null"));
    }
    // -------------------------
    // 1. Ownership Check (ensures that caller can act on request)
    // -------------------------
    return accessRequestDao
        .ownershipCheck(requestId, providerId, providerOrganizationId, isUserOrgAdmin)
        .compose(
            owned -> {
              if (!owned) {
                return Future.failedFuture(
                    new DxForbiddenException("User cannot update this request"));
              }
              // fetch the request DTO
              return accessRequestDao.get(requestId);
            })

        // -------------------------
        // Fetch request + item metadata
        // -------------------------
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(new DxNotFoundException("Access request not found"));
              }
              if (request.getStatus() != Status.PENDING) {
                return Future.failedFuture(
                    new DxValidationException("Request cannot be updated; not in PENDING state"));
              }

              // validate expiryAt again at service-layer (defence-in-depth)
              if (expiryAt != null && expiryAt.isBefore(LocalDateTime.now())) {
                return Future.failedFuture(
                    new DxValidationException(
                        "Invalid expiry time; expiryAt must be in the future"));
              }

              // get item metadata from catalogue
              UUID itemId = UUID.fromString(request.getItemId());
              GetItemRequest itemReq = new GetItemRequest(itemId.toString(), "");

              return itemService
                  .getItem(itemReq)
                  .map(
                      itemResponse -> {
                        Map<String, Object> ctx = new HashMap<>();
                        ctx.put("request", request); // safe
                        ctx.put(
                            "itemResponse", itemResponse.getElasticsearchResponses().getFirst());
                        ctx.put("itemId", itemId);
                        return ctx;
                      });
            })

        // -------------------------
        // 3. Extract constraints & validate
        // -------------------------
        .compose(
            ctxObj -> {
              Map<String, Object> ctx = ctxObj;

              AccessRequestDto request = (AccessRequestDto) ctx.get("request");
              JsonObject itemResponse = (JsonObject) ctx.get("itemResponse");
              UUID itemId = (UUID) ctx.get("itemId");

              JsonArray resourceServers =
                  itemResponse.getJsonArray("resourceServer", new JsonArray());

              Set<String> allowedAccessTypes = getAllowedAccessTypes(resourceServers);

              // Provider constraints are authoritative.
              // If null → derive from allowedAccessTypes.
              final JsonObject requestedConstraints;

              if (constraints != null) {
                requestedConstraints = constraints;
              } else {

                JsonArray accessArray = new JsonArray();

                for (String type : allowedAccessTypes) {
                  accessArray.add(new JsonObject().put("accessType", type));
                }

                requestedConstraints = new JsonObject().put("access", accessArray);
              }

              // validate; throws DxValidationException on invalid constraints
              validateAccessConstraints(requestedConstraints, allowedAccessTypes);

              // ------------------------------------------
              // CHECK IF ACTIVE POLICY ALREADY EXISTS
              // ------------------------------------------
              return policyDao
                  .checkExistingPoliciesForIds(
                      itemId, UUID.fromString(request.getProviderId()), request.getConsumerId())
                  .compose(
                      res -> {
                        if (!res.getRows().isEmpty()) {
                          // ACTIVE policy already exists → reject
                          return Future.failedFuture(
                              new DxValidationException(
                                  "Active policy already exists for this user & item"));
                        }

                        // No existing policy → SAFE TO CREATE POLICY
                        return createPolicyForApproval(
                            request.getItemOrganizationId(),
                            request.getConsumerId(),
                            itemId.toString(),
                            ItemType.fromCatalogueItemType(request.getAssetType()),
                            requestedConstraints,
                            expiryAt,
                            request.getProviderId(),
                            request.getAdditionalInfo(),
                            providerComment,
                            feedbackToConsumer);
                      })
                  .map(
                      policyId -> {
                        ctx.put("policyId", policyId);
                        ctx.put("constraints", requestedConstraints);
                        ctx.put("request", request);
                        return ctx;
                      });
            })

        // -------------------------
        // 4. Update AccessRequest status → GRANTED
        // -------------------------
        .compose(
            ctx -> {
              AccessRequestDto req = (AccessRequestDto) ctx.get("request");
              JsonObject requestedConstraints = (JsonObject) ctx.get("constraints");
              UUID itemId = UUID.fromString(req.getItemId());

              return accessRequestDao
                  .approveAccessRequest(requestId, "GRANTED", expiryAt)
                  .compose(
                      updated -> {
                        // create access rule if subjects present
                        UUID policyId = (UUID) ctx.get("policyId");
                        return createAccessRule(
                                policyId,
                                itemId,
                                UUID.fromString(req.getProviderId()),
                                requestedConstraints,
                                String.valueOf(expiryAt))
                            .recover(
                                err -> {
                                  LOGGER.error("Access rule creation failed", err);
                                  return Future.succeededFuture();
                                })
                            .map(
                                v -> {
                                  req.setStatus(Status.GRANTED);
                                  req.setExpiryAt(expiryAt);
                                  return req;
                                });
                      });
            });
  }

  private Future<Void> createAccessRule(
      UUID policyId, UUID itemId, UUID providerId, JsonObject constraints, String expiryAt) {

    if (constraints == null) {
      return Future.succeededFuture();
    }

    JsonObject subjects = constraints.getJsonObject("subjects");

    if (subjects == null || subjects.isEmpty()) {
      return Future.succeededFuture();
    }

    return accessRuleDao.createRule(policyId, itemId, providerId, subjects, constraints, expiryAt);
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
            "Requested access type '" + type + "' is not allowed for this resource");
      }
    }
  }

  private Future<UUID> createPolicyForApproval(
      String itemOrganizationId,
      String consumerId,
      String itemId,
      ItemType itemType,
      JsonObject constraints,
      LocalDateTime expiryAt,
      String providerUserId,
      JsonObject additionalInfo,
      String providerComment,
      String feedbackToConsumer) {

    CreatePolicyRequest request = new CreatePolicyRequest();
    request.setUserId(consumerId);
    request.setItemId(itemId);
    request.setItemType(itemType);
    request.setItemOrganizationId(itemOrganizationId);

    // expiry
    if (expiryAt != null) {
      request.setExpiryTime(expiryAt.toString());
    } else {
      request.setExpiryTime(null); // default expiryDays gets applied internally
    }

    // constraints & optional fields
    request.setConstraints(constraints);
    request.setAdditionalInfo(additionalInfo);
    request.setProviderComment(providerComment);
    request.setFeedbackToConsumer(feedbackToConsumer);

    List<CreatePolicyRequest> list = new ArrayList<>();
    list.add(request);

    return policyDao
        .insertPolicies(list, UUID.fromString(providerUserId))
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new RuntimeException("Policy insert returned empty result");
              }

              QueryResult firstResult = results.getFirst();

              // assuming QueryResult exposes rows as JsonObject
              JsonObject row = firstResult.getRows().getJsonObject(0);

              return UUID.fromString(row.getString("_id"));
            })
        .onSuccess(v -> LOGGER.info("Policy created for consumer {}", consumerId))
        .onFailure(err -> LOGGER.error("Policy create failed: {}", err.getMessage(), err));
  }

  @Override
  public Future<AccessRequestDto> rejectAccessRequest(
      UUID providerId,
      UUID requestId,
      UUID providerOrganizationId,
      boolean isUserOrgAdmin,
      String providerComment,
      String feedbackToConsumer) {

    if (providerOrganizationId == null) {
      LOGGER.error("Provider organization ID is null for requestId: {}", requestId);
      return Future.failedFuture(
          new DxForbiddenException("Provider organization ID in the token cannot be null"));
    }

    return accessRequestDao
        .ownershipCheck(requestId, providerId, providerOrganizationId, isUserOrgAdmin)
        .compose(
            owned -> {
              if (!owned) {
                return Future.failedFuture(
                    new DxForbiddenException("User cannot update this request"));
              }
              return accessRequestDao.get(requestId);
            })
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(new DxNotFoundException("Access request not found"));
              }

              UUID itemId = UUID.fromString(request.getItemId());
              UUID ownerId = UUID.fromString(request.getProviderId());
              String consumerId = request.getConsumerId();

              // Delete policy if exists
              return policyDao
                  .deActivatePolicyByUserAndItem(itemId, ownerId, consumerId)
                  .compose(
                      queryResult -> {
                        if (queryResult.getRows() == null || queryResult.getRows().isEmpty()) {
                          // No policy existed — continue safely
                          return Future.succeededFuture();
                        }

                        JsonObject row = queryResult.getRows().getJsonObject(0);
                        UUID policyId = UUID.fromString(row.getString(DB_ID));

                        // deactivate associated access rules
                        return accessRuleDao.updateStatusByPolicyId(policyId, IN_ACTIVE);
                      })
                  .recover(
                      err -> {
                        LOGGER.warn("Policy delete skipped or failed: {}", err.getMessage());
                        return Future.succeededFuture();
                      })
                  .compose(
                      v -> {

                        // Update request status
                        Map<String, Object> conditions =
                            Map.of(DB_REQUEST_ID, requestId.toString());

                        Map<String, Object> updates =
                            Map.of(DB_STATUS, Status.REJECTED.getStatus());

                        return accessRequestDao
                            .update(conditions, updates)
                            .map(
                                updateResult -> {
                                  request.setStatus(Status.REJECTED);
                                  return request;
                                });
                      });
            })
        .onSuccess(v -> LOGGER.info("Access request rejected and policy removed: {}", requestId))
        .onFailure(err -> LOGGER.error("Failed to reject access request: {}", requestId, err));
  }

  @Override
  public Future<HasAccessResponse> checkAccessRequest(UUID consumerId, String itemId) {

    UUID itemUuid = UUID.fromString(itemId);

    Future<Boolean> requestAccessFuture =
        accessRequestDao.hasAccess(consumerId.toString(), itemId).otherwise(false);

    Future<List<PolicyAccessInfo>> policiesFuture =
        policyDao.getMatchingPolicies(itemUuid, consumerId.toString()).otherwise(List.of());

    Future<JsonObject> ruleFuture =
        keycloakUserService
            .getUserById(consumerId)
            .compose(
                fullUser ->
                    accessRuleDao.findMatchingRule(
                        itemUuid,
                        fullUser.sub().toString(),
                        fullUser.organisationId(),
                        fullUser.roles()))
            .otherwiseEmpty();

    return Future.all(requestAccessFuture, policiesFuture, ruleFuture)
        .compose(
            composite -> {
              Boolean requestAccess = composite.resultAt(0);

              List<PolicyAccessInfo> policies = composite.resultAt(1);

              JsonObject rule = composite.resultAt(2);

              List<PolicyAccessInfo> results = new ArrayList<>(policies);

              // Add rule result if exists
              if (rule != null) {
                String expiryAt = rule.getString(EXPIRY_AT);

                results.add(
                    new PolicyAccessInfo(
                        UUID.fromString(rule.getString(POLICY_ID)),
                        rule.getJsonObject(CONS, new JsonObject()),
                        expiryAt != null
                            ? LocalDateTime.parse(expiryAt)
                            : null));
              }

              boolean hasAccess =
                  Boolean.TRUE.equals(requestAccess) || !policies.isEmpty() || rule != null;

              if (!hasAccess) {

                return Future.failedFuture(
                    new DxForbiddenNoAccessException(
                        "User does not have access to the given item"));
              }

              PaginationInfo pagination = PaginationInfo.from(1, results.size(), results.size());

              return Future.succeededFuture(new HasAccessResponse(results, pagination));
            })
        .onFailure(
            err ->
                LOGGER.error(
                    "Failed to check access for consumer {} on item {}", consumerId, itemId, err));
  }

  @Override
  public Future<PaginatedResult<AccessRequestDto>> listAccessRequestForConsumer(
      PaginatedRequest paginatedRequest) {
    return accessRequestDao.getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<PaginatedResult<AccessRequestDto>> listAccessRequestForProvider(
      PaginatedRequest paginatedRequest) {
    return accessRequestDao.getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<PaginatedResult<AccessRequestDto>> enrichAccessRequestsWithItemDetails(
      PaginatedResult<AccessRequestDto> pagedResult) {

    List<Future<?>> futures = new ArrayList<>();

    for (AccessRequestDto dto : pagedResult.data()) {

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
                      dto.setProviderId(asset.getProviderId());
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
  public Future<AccessRequestDto> updateAccessRequestForConsumer(UUID consumerId, UUID requestId) {
    return accessRequestDao
        .get(requestId)
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(new DxNotFoundException("Access request not found"));
              }

              boolean isOwner =
                  request.getConsumerId() != null
                      && request.getConsumerId().equals(consumerId.toString());
              if (!isOwner) {
                return Future.failedFuture(
                    new DxForbiddenException("User cannot withdraw this request"));
              }

              if (!Status.PENDING.equals(request.getStatus())) {
                return Future.failedFuture(
                    new DxValidationException("Only pending requests can be withdraw"));
              }

              Map<String, Object> conditions = Map.of(DB_REQUEST_ID, requestId.toString());
              Map<String, Object> updates = Map.of(DB_STATUS, Status.WITHDRAWN.getStatus());

              return accessRequestDao
                  .update(conditions, updates)
                  .compose(
                      updateResult -> {
                        request.setStatus(Status.WITHDRAWN);
                        return Future.succeededFuture(request);
                      });
            })
        .onSuccess(
            v -> LOGGER.info("Withdrew access request {} by consumer {}", requestId, consumerId))
        .onFailure(
            err ->
                LOGGER.error(
                    "Failed to withdraw access request {}: {}", requestId, err.getMessage()));
  }

  private Asset parseAndGetAsset(JsonObject result, String id) {
    LOGGER.debug("Asset info : {}", result.encodePrettily());
    try {
      String assetName = result.getString(ASSET_NAME_KEY, "").trim();
      String provider = result.getString(OWNER_ID);
      String organizationId = result.getString(ORGANIZATION_ID);
      String shortDescription = result.getString(SHORT_DESCRIPTION, "").trim();
      String organizationName = result.getString(ORGANIZATION, "");

      AssetType catAssetType = null;
      JsonArray typeArray = result.getJsonArray(TYPE);
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
}
