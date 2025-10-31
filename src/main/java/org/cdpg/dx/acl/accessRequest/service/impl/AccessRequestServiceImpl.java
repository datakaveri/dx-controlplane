package org.cdpg.dx.acl.accessRequest.service.impl;

import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;
import static org.cdpg.dx.catalogueService.config.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.dao.model.AssetType;
import org.cdpg.dx.acl.accessRequest.dao.model.Status;
import org.cdpg.dx.acl.accessRequest.service.AccessRequestService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.catalogueService.models.Asset;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.RequestType;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public class AccessRequestServiceImpl implements AccessRequestService {

  private static final Logger LOGGER = LogManager.getLogger(AccessRequestServiceImpl.class);

  private final AccessRequestDao accessRequestDao;
  private final ItemService itemService;

  public AccessRequestServiceImpl(ItemService itemService, AccessRequestDao accessRequestDao) {
    this.itemService = itemService;
    this.accessRequestDao = Objects.requireNonNull(accessRequestDao);
  }

  @Override
  public Future<AccessRequestDto> createAccessRequest(
      DxUser consumer, UUID itemId, RequestType requestType, JsonObject additionalInfo) {
    AccessRequestDto accessRequestDto =
        new AccessRequestDto()
            .setRequestType(requestType)
            .setConsumerId(consumer.sub().toString())
            .setConsumerEmail(consumer.email())
            .setConsumerFirstName(consumer.givenName())
            .setConsumerLastName(consumer.familyName())
            .setConsumerOrganization(consumer.organisationName());

    if (additionalInfo != null) {
      accessRequestDto.setAdditionalInfo(additionalInfo);
    }

    return accessRequestDao
        .isAccessRequestPresent(consumer.sub(), itemId)
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
                String message = "Item not found for ID: " + itemId;
                LOGGER.error(message);
                return Future.failedFuture(new DxForbiddenException(message));
              }
              JsonObject itemJson = responseModel.getElasticsearchResponses().getFirst();
              Asset asset = parseAndGetAsset(itemJson, itemId.toString());

              /*Forbidden if the provider id is equal to the consumer id
              as provider cannot create an access request for his resource*/
              if (asset.getProviderId().equals(consumer.sub().toString())) {
                String message = "Provider cannot create an access request for their own resource";
                LOGGER.warn(message);
                return Future.failedFuture(new DxCreateAccessRequestForbiddenException(message));
              }

              accessRequestDto
                  .setAssetType(asset.getAssetType())
                  .setAssetName(asset.getAssetName())
                  .setProviderId(asset.getProviderId())
                  .setItemOrganization(asset.getOrganizationId())
                  .setShortDescription(asset.getShortDescription())
                  .setItemId(asset.getItemId());
              return accessRequestDao.create(accessRequestDto);
            })
        .compose(
            dto -> {
              LOGGER.info("Access request created successfully: {}", dto);
              return Future.succeededFuture(dto);
            })
        .recover(
            error -> {
              LOGGER.error("Failed to create access request", error);
              return Future.failedFuture(error);
            });
  }

  @Override
  public Future<AccessRequestDto> approveAccessRequest(
      UUID providerId,
      UUID requestId,
      LocalDateTime expiryAt,
      UUID providerOrganizationId,
      boolean isUserOrgAdmin) {
    if (providerOrganizationId == null) {
      LOGGER.error("Provider organization ID is null for requestId: {}", requestId);
      return Future.failedFuture(
          new DxForbiddenException("Provider organization ID in the token, cannot be null"));
    }
    return accessRequestDao
        .ownershipCheck(requestId, providerId, providerOrganizationId, isUserOrgAdmin)
        .compose(
            ownershipCheckSucceeded -> {
              if (ownershipCheckSucceeded) {
                return accessRequestDao.approveAccessRequest(requestId, "GRANTED", expiryAt);
              } else {
                return Future.failedFuture(
                    new DxForbiddenException("User can not update this request"));
              }
            });
  }

  @Override
  public Future<AccessRequestDto> rejectAccessRequest(
      UUID providerId, UUID requestId, UUID providerOrganizationId, boolean isUserOrgAdmin) {

    if (providerOrganizationId == null) {
      LOGGER.error("Provider organization ID is null for requestId: {}", requestId);
      return Future.failedFuture(
          new DxForbiddenException("Provider organization ID in the token, cannot be null"));
    }
    return accessRequestDao
        .ownershipCheck(requestId, providerId, providerOrganizationId, isUserOrgAdmin)
        .compose(
            owned -> {
              Map<String, Object> conditions = Map.of(DB_REQUEST_ID, requestId.toString());
              Map<String, Object> updates = Map.of(DB_STATUS, Status.REJECTED.getStatus());
              return accessRequestDao.update(conditions, updates);
            })
        .onSuccess(v -> LOGGER.info("Access request rejected: {}", requestId))
        .onFailure(err -> LOGGER.error("Failed to reject access request: {}", requestId, err));
  }

  @Override
  public Future<Boolean> checkAccessRequest(UUID consumerId, String itemId) {
    return accessRequestDao
        .hasAccess(consumerId.toString(), itemId)
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
  public Future<AccessRequestDto> updateAccessRequestForConsumer(UUID consumerId, UUID requestId) {
    return accessRequestDao.get(requestId)
      .compose(request -> {
        if (request == null) {
          return Future.failedFuture(new DxNotFoundException("Access request not found"));
        }

        boolean isOwner = request.getConsumerId() != null &&
          request.getConsumerId().equals(consumerId.toString());
        if (!isOwner) {
          return Future.failedFuture(new DxForbiddenException("User cannot withdraw this request"));
        }

        if (!Status.PENDING.equals(request.getStatus())) {
          return Future.failedFuture(new DxValidationException("Only pending requests can be withdraw"));
        }

        Map<String, Object> conditions = Map.of(DB_REQUEST_ID, requestId.toString());
        Map<String, Object> updates = Map.of(DB_STATUS, Status.WITHDRAWN.getStatus());

        return accessRequestDao.update(conditions, updates)
          .compose(updateResult -> {
            request.setStatus(Status.WITHDRAWN);
            return Future.succeededFuture(request);
          });
      })
      .onSuccess(v -> LOGGER.info("Withdrew access request {} by consumer {}", requestId, consumerId))
      .onFailure(err -> LOGGER.error("Failed to withdraw access request {}: {}", requestId, err.getMessage()));
  }


  private Asset parseAndGetAsset(JsonObject result, String id) {
    LOGGER.debug("Asset info : {}", result.encodePrettily());
    try {
      String assetName = result.getString(ASSET_NAME_KEY, "").trim();
      String provider = result.getString(OWNER_ID);
      String organizationId = result.getString(ORGANIZATION_ID);
      String shortDescription = result.getString(SHORT_DESCRIPTION, "").trim();

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
          .setAssetType(catAssetType.getAssetType())
          .setAssetName(assetName)
          .setShortDescription(shortDescription);

    } catch (Exception e) {
      LOGGER.error("Error building asset from catalogue metadata: {}", e.getMessage(), e);
      throw new DxInternalServerErrorException("Incomplete asset metadata from catalogue");
    }
  }
}
