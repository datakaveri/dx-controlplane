package org.cdpg.dx.aaa.accessRequest.service.impl;

import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.DB_STATUS;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.aaa.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.aaa.accessRequest.dao.model.Status;
import org.cdpg.dx.aaa.accessRequest.service.AccessRequestService;
import org.cdpg.dx.catalogueService.service.CatalogueService;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxCreateAccessRequestForbiddenException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.RequestType;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public class AccessRequestServiceImpl implements AccessRequestService {

  private static final Logger LOGGER = LogManager.getLogger(AccessRequestServiceImpl.class);

  private final CatalogueService catalogueService;
  private final AccessRequestDao accessRequestDao;

  public AccessRequestServiceImpl(
      CatalogueService catalogueService, AccessRequestDao accessRequestDao) {
    this.catalogueService = Objects.requireNonNull(catalogueService);
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
              return catalogueService.fetchAsset(itemId.toString());
            })
        .compose(
            asset -> {
              LOGGER.debug("Fetched asset: {}", asset);
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
}
