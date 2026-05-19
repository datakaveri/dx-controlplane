package org.cdpg.dx.acl.policy.dao;

import io.vertx.core.Future;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cdpg.dx.acl.accessRequest.dao.model.PolicyAccessInfo;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.QueryResult;

public interface PolicyDao extends BaseDAO<PolicyDto> {
  Future<QueryResult> checkExistingPoliciesForIds(UUID itemId, UUID ownerId, String userEmail);

  Future<QueryResult> checkExistingPoliciesForIds(List<CreatePolicyRequest> requests, UUID ownerId);

  Future<List<QueryResult>> insertPolicies(List<CreatePolicyRequest> requests, UUID userId);

  Future<QueryResult> getPoliciesByConsumer(String consumerId);

  Future<QueryResult> getPoliciesByProvider(String ownerId);

  Future<QueryResult> verifyPolicy(UUID policyId);

  Future<QueryResult> deActivatePolicy(UUID policyId);

  Future<QueryResult> deActivatePolicyByUserAndItem(UUID itemId, UUID ownerId, String userEmail);

  Future<PaginatedResult<PolicyDto>> getPoliciesWithAccessControl(PaginatedRequest request,
                                                                  Set<String> policyIds,
                                                                  String consumerId);
  Future<List<PolicyAccessInfo>> getMatchingPolicies(UUID itemId, String consumerId);
}
