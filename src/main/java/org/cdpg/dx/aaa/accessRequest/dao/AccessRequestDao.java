package org.cdpg.dx.aaa.accessRequest.dao;

import io.vertx.core.Future;
import java.time.LocalDateTime;
import java.util.UUID;

import org.cdpg.dx.aaa.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

public interface AccessRequestDao extends BaseDAO<AccessRequestDto> {
  Future<Boolean> isAccessRequestPresent(UUID consumerId, UUID itemId);

  Future<Boolean> hasAccess(String consumerId, String itemId);

  Future<Boolean> ownershipCheck(UUID requestId, UUID providerId, UUID providerOrganizationId, boolean isUserOrgAdmin);

  Future<AccessRequestDto> approveAccessRequest(UUID requestId, String granted, LocalDateTime expiryTime);
}