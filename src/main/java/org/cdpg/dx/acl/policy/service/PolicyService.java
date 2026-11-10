package org.cdpg.dx.acl.policy.service;

import io.vertx.core.Future;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.dao.model.VerifyPolicyDto;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public interface PolicyService {
  default Future<Void> createPolicy(List<CreatePolicyRequest> policy, DxUser caller) {
    return createPolicy(policy, caller, null);
  }

  /**
   * @param deptScopedNodeIds the dept_admin caller's department subtree, or null when the caller
   *     is not a dept_admin
   */
  Future<Void> createPolicy(
      List<CreatePolicyRequest> policy, DxUser caller, List<String> deptScopedNodeIds);

  Future<List<PolicyDto>> getPolicy(DxUser caller);

  default Future<Void> deActivatePolicy(String policyId, DxUser user) {
    return deActivatePolicy(policyId, user, null);
  }

  /**
   * @param deptScopedNodeIds the dept_admin caller's department subtree, or null when the caller
   *     is not a dept_admin
   */
  Future<Void> deActivatePolicy(String policyId, DxUser user, List<String> deptScopedNodeIds);

  Future<List<VerifyPolicyDto>> initiateVerifyPolicy(
      UUID ownerId, String userEmail, UUID itemId, ItemType itemType, DxUser user);

  Future<PaginatedResult<PolicyDto>> listPolicies(PaginatedRequest request);
  Future<PaginatedResult<PolicyDto>> listPolicies(
      PaginatedRequest request,
      Set<String> policyIds,
      String consumerId);

  Future<PaginatedResult<PolicyDto>> enrichPolicyRequestsWithItemDetails(
      PaginatedResult<PolicyDto> pagedResult);

  Future<PaginatedResult<PolicyDto>> enrichPolicyRequestsWithUserInfo(
      PaginatedResult<PolicyDto> pagedResult);

  Future<Boolean> hasActivePolicies(UUID assetId);
}
