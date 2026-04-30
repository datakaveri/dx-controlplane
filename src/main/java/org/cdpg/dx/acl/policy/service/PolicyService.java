package org.cdpg.dx.acl.policy.service;

import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.dao.model.VerifyPolicyDto;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public interface PolicyService {
  Future<Void> createPolicy(List<CreatePolicyRequest> policy, DxUser caller);
  Future<List<PolicyDto>> getPolicy(DxUser caller);
  Future<Void> deActivatePolicy(String policyId, DxUser user);
  Future<VerifyPolicyDto> initiateVerifyPolicy(UUID ownerId, String userEmail, UUID itemId,
                                               ItemType itemType, DxUser user);

  Future<PaginatedResult<PolicyDto>> listPolicies(PaginatedRequest request);

  Future<PaginatedResult<PolicyDto>> enrichPolicyRequestsWithItemDetails(
      PaginatedResult<PolicyDto> pagedResult);

  Future<PaginatedResult<PolicyDto>> enrichPolicyRequestsWithUserInfo(
      PaginatedResult<PolicyDto> pagedResult);
}
