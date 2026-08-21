package org.cdpg.dx.aaa.delegation.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;

@VertxGen
@ProxyGen
public interface DelegationService {

  @GenIgnore
  static DelegationService createProxy(Vertx vertx, String address) {
    return new DelegationServiceVertxEBProxy(vertx, address);
  }

  /**
   * Targeted lookup: fetches the single active delegation from {@code delegatorId} to
   * {@code delegateeId}, merging all non-expired constraints from all active grants into
   * a single response JsonObject with a "constraints" array.
   * Fails with {@code DxNotFoundException} when no matching active delegation exists.
   */
  Future<JsonObject> findActiveDelegation(String delegatorId, String delegateeId);

  Future<JsonObject> createDelegationGrant(JsonObject delegationGrant, List<String> UserRoles, JsonArray roleConstraints,String orgId);

  Future<JsonObject> getDelegationGrantById(String delegationId);

  Future<List<JsonObject>> getDelegationScopeByEntityId(String entity);

//  Future<DelegationUpdateRequest> createDelegationRequest(DelegationUpdateRequest delegationRequest,Set<String> UserRoles,List<JsonObject> constraintsJson,String delegatorId);
//
//  Future<DelegationUpdateRequest> updateDelegationRequestStatus(String requestId, String status,String delegatorId);

  Future<List<JsonObject>> getAllDelegationsOfDelegate(String userId);

  Future<List<JsonObject>> getAllDelegationsByDelegator(String userId);

  Future<Boolean> deleteDelegation(String delegationId,String userId);

  Future<List<JsonObject>> getDelegationRequestsByDelegationId(String delegationId);

  Future<List<JsonObject>> getDelegationScopeConstraints(String delegationId);

  Future<List<JsonObject>> getAllDelegationScopeConstraints(String itemId);

  Future<JsonObject> checkItemAccess(String delegatorId, String delegateId);

  Future<JsonObject> getDelegatorRoles(String userId, String delegatorId);

  Future<JsonObject> appendDelegationConstraints(
      String delegationId, String userId, JsonArray roles, String orgId);

  Future<JsonObject> removeDelegationConstraints(String delegationId, String userId,
                                                 JsonArray roles);

  Future<JsonObject> rejectDelegation(String delegationId, String delegateId);
}
