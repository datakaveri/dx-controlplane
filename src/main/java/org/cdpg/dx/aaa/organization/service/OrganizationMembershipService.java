package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.List;
import java.util.UUID;

public interface OrganizationMembershipService {

  Future<OrganizationJoinRequest> joinOrganizationRequest(OrganizationJoinRequest organizationJoinRequest);

  Future<OrganizationJoinRequest> getOrganizationJoinRequestById(UUID requestId);

  Future<PaginatedResult<OrganizationJoinRequest>> getOrganizationPendingJoinRequests(PaginatedRequest paginatedRequest);

  Future<Boolean> updateOrganizationJoinRequestStatus(UUID requestId, Status status);

  Future<OrganizationJoinRequest> withdrawJoinRequest(UUID userId, UUID requestId);

  Future<Boolean> addUserToOrganizationFromRequest(UUID requestId);

  Future<List<OrganizationJoinRequest>> getOrganizationJoinRequestsByUser(UUID userId);

  Future<List<OrganizationJoinRequest>> getOrganizationJoinRequestsByOrgId(UUID orgId);

  Future<List<OrganizationJoinRequest>> getAllOrganizationJoinRequests();

  Future<PaginatedResult<OrganizationUser>> getOrganizationUsers(PaginatedRequest paginatedRequest);

  Future<Boolean> updateUserRole(UUID orgId, UUID userId, Role role);

  Future<Boolean> deleteOrganizationUser(UUID orgId, UUID userId);

  Future<Boolean> deleteProviderUser(UUID userId, UUID orgAdminId, UUID orgId);

  Future<OrganizationUser> getOrganizationUserInfo(UUID userId);

  Future<OrganizationUser> getOrganisationUserByUserId(UUID userId);

  Future<List<OrganizationUser>> getOrganisationAdminId(UUID orgId);

  Future<Boolean> isOrgAdmin(UUID orgid, UUID userid);

  Future<UUID> getUserOrgAdminId(UUID orgId);

  Future<Boolean> deleteOrganizationJoinRequest(UUID orgId, UUID userId);

  Future<Boolean> deleteOrganizationJoinRequestById(UUID requestId);

  Future<List<JsonObject>> enrichWithUserInfo(List<JsonObject> computeReqs);
}
