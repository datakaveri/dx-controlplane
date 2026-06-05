package org.cdpg.dx.acl.accessRequest.dao;

import io.vertx.core.Future;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestSummary;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

public interface AccessRequestDao extends BaseDAO<AccessRequestDto> {
  Future<Boolean> isAccessRequestPresent(UUID consumerId, UUID itemId);

  Future<List<AccessRequestDto>> getActivePendingRequests(UUID consumerId, UUID itemId);

  Future<Boolean> hasAccess(String consumerId, String itemId);

  Future<Boolean> ownershipCheck(UUID requestId, UUID providerId, UUID providerOrganizationId, boolean isUserOrgAdmin);

  Future<AccessRequestDto> approveAccessRequest(UUID requestId, String granted, LocalDateTime expiryTime);

  Future<AccessRequestSummary> getAccessSummary(String consumerId, String itemId);
}
