package org.cdpg.dx.aaa.delegation.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationUpdateRequest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


public interface DelegationService {
  Future<DelegationGrant> createDelegationGrant(DelegationGrant delegationGrant, Set<String> UserRoles);

  Future<DelegationGrant> getDelegationGrantById(UUID delegationId);

  Future<DelegationUpdateRequest> createDelegationRequest(DelegationUpdateRequest delegationRequest,Set<String> UserRoles);

  Future<DelegationUpdateRequest> updateDelegationRequestStatus(UUID requestId, String status,UUID delegatorId);

  Future<DelegationUpdateRequest> getDelegationRequestsByUser(UUID userId);
}
