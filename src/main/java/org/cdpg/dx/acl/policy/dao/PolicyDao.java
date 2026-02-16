package org.cdpg.dx.acl.policy.dao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.ResourceObj;
import org.cdpg.dx.database.postgres.models.QueryResult;


public interface PolicyDao {
  Future<QueryResult> checkExistingPoliciesForIds(UUID itemId, UUID ownerId, String userEmail);
  Future<QueryResult> checkExistingPoliciesForIds(List<CreatePolicyRequest> requests, UUID ownerId);

  Future<List<QueryResult>> insertPolicies(List<CreatePolicyRequest> requests, UUID userId);

  Future<QueryResult> getPoliciesByConsumer(String email);

  Future<QueryResult> getPoliciesByProvider(String ownerId);

  Future<QueryResult> verifyPolicy(UUID policyId);

  Future<QueryResult> deletePolicy(UUID policyId);
  Future<QueryResult> deletePolicyByUserAndItem(
      UUID itemId,
      UUID ownerId,
      String userEmail);
}