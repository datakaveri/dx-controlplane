package org.cdpg.dx.aaa.accessRequest.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.UUID;

import org.cdpg.dx.aaa.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.RequestType;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public interface AccessRequestService {
  Future<AccessRequestDto> createAccessRequest(DxUser consumer, UUID itemId, RequestType requestType,
                                               JsonObject additionalInfo);

  Future<AccessRequestDto> approveAccessRequest(UUID providerId, UUID requestId, LocalDateTime expiryAt,
                                                UUID providerOrganizationId, boolean isUserOrgAdmin);

  Future<AccessRequestDto> rejectAccessRequest(UUID providerId, UUID requestId, UUID providerOrganizationId, boolean isUserOrgAdmin);

  Future<Boolean> checkAccessRequest(UUID userId, String itemId);

  Future<PaginatedResult<AccessRequestDto>> listAccessRequestForConsumer(PaginatedRequest paginatedRequest);

  Future<PaginatedResult<AccessRequestDto>> listAccessRequestForProvider(PaginatedRequest paginatedRequest);


}