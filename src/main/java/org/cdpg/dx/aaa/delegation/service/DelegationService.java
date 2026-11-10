package org.cdpg.dx.aaa.delegation.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import org.cdpg.dx.aaa.delegation.models.DelegationsPaginatedResponse;
import org.cdpg.dx.common.request.PaginatedRequest;

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

  /**
   * Paginated variant of {@link #getAllDelegationsOfDelegate(String)}.
   *
   * <p>Marked {@code @GenIgnore} because {@link PaginatedRequest} and {@link DelegationsPaginatedResponse}
   * are plain records, not Vert.x codegen types, so they cannot cross the event bus. It is a
   * {@code default} method so the generated event-bus proxy still implements the interface; the
   * REST path uses the in-process {@code DelegationServiceImpl}, which overrides it.
   */
  @GenIgnore
  default Future<DelegationsPaginatedResponse> getAllDelegationsOfDelegate(
      String userId, PaginatedRequest request) {
    return Future.failedFuture(
        new UnsupportedOperationException(
            "Paginated delegation listing is not available over the event bus proxy"));
  }

  /** Paginated variant of {@link #getAllDelegationsByDelegator(String)}. See the note above. */
  @GenIgnore
  default Future<DelegationsPaginatedResponse> getAllDelegationsByDelegator(
      String userId, PaginatedRequest request) {
    return Future.failedFuture(
        new UnsupportedOperationException(
            "Paginated delegation listing is not available over the event bus proxy"));
  }

  Future<Boolean> deleteDelegation(String delegationId,String userId);

  Future<Boolean> deactivateDelegation(String delegationIdStr, String userIdStr);

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
