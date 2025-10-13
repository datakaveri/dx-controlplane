package org.cdpg.dx.aaa.delegation.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.delegation.models.DelegationUpdateRequest;
import org.cdpg.dx.common.util.ExceptionHttpStatusMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public interface DelegationService {
  Future<DelegationGrant> createDelegationGrant(DelegationGrant delegationGrant, Set<String> UserRoles,List<JsonObject>constraints);

  Future<DelegationGrant> getDelegationGrantById(UUID delegationId);

  Future<DelegationUpdateRequest> createDelegationRequest(DelegationUpdateRequest delegationRequest,Set<String> UserRoles,List<JsonObject> constraintsJson,UUID delegatorId);

  Future<DelegationUpdateRequest> updateDelegationRequestStatus(UUID requestId, String status,UUID delegatorId);

  Future<List<DelegationUpdateRequest>> getDelegationRequestsByUser(UUID userId);

  Future<List<DelegationUpdateRequest>> getDelegationRequestsByDelegationId(UUID delegationId);

  Future<List<DelegationScopeConstraint>> getDelegationScopeConstraints(UUID delegationId);

}
