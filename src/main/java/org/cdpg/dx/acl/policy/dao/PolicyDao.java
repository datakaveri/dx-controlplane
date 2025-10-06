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
  Future<Set<UUID>> checkForItemsInDb(Set<UUID> itemIds, Set<String> itemTypes, DxUser dxUser);

  Future<Boolean> checkExistingPoliciesForId(List<CreatePolicyRequest> requests, UUID providerId);
  Future<JsonObject> checkExistingPoliciesForId(UUID itemId, UUID ownerId, String userEmailId);

  Future<List<QueryResult>> insertPolicies(List<CreatePolicyRequest> requests, UUID userId);

  Future<Set<UUID>> insertItemsIntoDb(List<ResourceObj> resourceObjList);

  Future<QueryResult> getPoliciesByConsumer(String email);

  Future<QueryResult> getPoliciesByProvider(String ownerId);

  Future<QueryResult> verifyPolicy(UUID policyId);

  Future<QueryResult> deletePolicy(UUID policyId);
}