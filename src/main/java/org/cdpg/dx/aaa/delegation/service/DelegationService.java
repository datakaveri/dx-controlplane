package org.cdpg.dx.aaa.delegation.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.delegation.models.DelegationUpdateRequest;


import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@VertxGen
@ProxyGen
public interface DelegationService {

  @GenIgnore
  static DelegationService createProxy(Vertx vertx, String address) {
    return new DelegationServiceVertxEBProxy(vertx, address);
  }
  Future<JsonObject> createDelegationGrant(JsonObject delegationGrant, Set<String> UserRoles, JsonArray roleConstraints);

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
}
