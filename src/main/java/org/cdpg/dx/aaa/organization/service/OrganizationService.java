package org.cdpg.dx.aaa.organization.service;


import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public interface OrganizationService {



  // ************ ORGANIZATION CREATE REQUEST *********
  Future<OrganizationCreateRequest> getOrganizationCreateRequestById(UUID requestId);

  Future<List<OrganizationCreateRequest>> getOrganizationCreateRequestsByUserId(UUID userId);

  Future<List<OrganizationCreateRequest>> getAllPendingGrantedOrganizationCreateRequests();
  Future <PaginatedResult<OrganizationCreateRequest>> getAllOrganizationCreateRequests(PaginatedRequest request);

  Future<OrganizationCreateRequest> createOrganizationRequest(OrganizationCreateRequest organizationCreateRequest);

  // status enum , adding a new org entry and updating orgCreateRequest
  // update orgUser role
  Future<Boolean> updateOrganizationCreateRequestStatus(UUID requestId, Status status);

//   ************ ORGANIZATION JOIN REQUEST *********

  Future<OrganizationJoinRequest> joinOrganizationRequest(OrganizationJoinRequest organizationJoinRequest);

  Future<PaginatedResult<OrganizationJoinRequest>> getOrganizationPendingJoinRequests(PaginatedRequest paginatedRequest);

  Future<OrganizationJoinRequest> getOrganizationJoinRequestById(UUID requestId);

  Future<OrganizationJoinRequest> withdrawJoinRequest(UUID userId, UUID requestId);

  Future<Boolean> updateOrganizationJoinRequestStatus(
      UUID requestId, Status Status, UUID approverUserId);

  Future<List<OrganizationJoinRequest>> getOrganizationJoinRequestsByUser(UUID userId);

  Future<List<OrganizationJoinRequest>> getAllOrganizationJoinRequests();

  Future<List<OrganizationJoinRequest>> getOrganizationJoinRequestsByOrgId(UUID orgId);
  // ************ ORGANIZATION *********
  Future<Boolean> deleteOrganization(UUID orgId);

  Future<List<Organization>> getOrganizations();

  Future<PaginatedResult<Organization>> getOrganizations(PaginatedRequest paginatedRequest);


  Future<Organization> getOrganizationById(UUID orgId);

  Future<Organization> getOrganizationByName(String orgName);

  //update organization
  Future<Organization> updateOrganizationById(UUID orgId,UpdateOrgDTO updateOrgDTO);

  // ************ ORGANIZATION USERS *********

  //delete from orgUser Table
  Future<Boolean> deleteOrganizationUser(UUID orgId,UUID userId);

  Future<Boolean> deleteProviderUser(UUID userId, UUID orgAdminId, UUID orgId);

  Future<PaginatedResult<OrganizationUser>> getOrganizationUsers(PaginatedRequest paginatedRequest); //UUID orgId

  Future<OrganizationUser> getOrganisationUserByUserId(UUID userId);

  Future<Boolean> updateUserRole(UUID orgId, UUID userId, Role Role);

  Future<Boolean> isOrgAdmin (UUID orgid, UUID userid);

  Future<OrganizationUser> getOrganizationUserInfo(UUID userId);

    Future<List<JsonObject>> enrichWithUserInfo(List<JsonObject> computeReqs);

  // to check - info about the users
 // Future<OrganizationUser> getOrganizationUserById(UUID userId);

  Future<List<OrganizationUser>> getOrganisationAdminId(UUID orgId);


  //############################Provider Request#############################

  Future<ProviderRoleRequest> createProviderRequest(ProviderRoleRequest providerRoleRequest);

  Future<Boolean> updateProviderRequestStatus(UUID requestId, Status status, UUID approverUserId);

  Future<List<ProviderRoleRequest>> getAllPendingProviderRoleRequests(UUID orgId);

  Future<PaginatedResult<ProviderRoleRequest>> getAllPendingProviderRoleRequests(PaginatedRequest paginatedRequest);

  Future<Boolean> hasPendingProviderRole(UUID userId, UUID orgId);

  Future<Boolean> createProviderRole(ProviderRoleRequest providerRoleRequest);

  Future<ProviderRoleRequest> getProviderRequestById(UUID requestId);

  Future<Boolean> deleteOrganizationJoinRequest(UUID orgId, UUID userId);

  Future<Boolean> deleteProviderRoleRequest(UUID orgId, UUID userId);

  Future<UUID> getUserOrgAdminId(UUID orgId);

  Future<Boolean> deleteOrganizationRequestById(UUID requestId);

  Future<Boolean> deleteOrganizationJoinRequestById(UUID requestId);

  Future<ProviderRoleRequest> getProviderRoleRequestByUserId(UUID userId);

  Future<Boolean> deleteProviderRoleRequestById(UUID id);

  Future<Boolean> hasPendingPlatformProviderRole(UUID userId);

  Future<PaginatedResult<ProviderRoleRequest>> getAllPlatformProviderRequests(PaginatedRequest request);

  Future<ProviderRoleRequest> getPlatformProviderRoleRequestByUserId(UUID userId);

  Future<List<OrganizationHierarchyNode>> getHierarchyNodes(UUID organizationId);

  Future<OrganizationHierarchyNode> getHierarchyNode(UUID organizationId, UUID nodeId);

  Future<List<OrganizationHierarchyNode>> getHierarchyNodeChildren(
      UUID organizationId, UUID nodeId);

  Future<List<OrganizationHierarchyNode>> getHierarchyNodeDescendants(
      UUID organizationId, UUID nodeId);

  Future<List<UUID>> getDescendantNodeIds(UUID organizationId, UUID nodeId);

  Future<Boolean> isAncestorOrSelfNode(
      UUID organizationId, UUID ancestorNodeId, UUID targetNodeId, boolean strict);

  Future<OrganizationHierarchyDefinitionRequest> createHierarchyDefinitionRequest(
      UUID organizationId, List<ProposedDepartmentNode> departments);

  Future<OrganizationHierarchyDefinitionRequest> getHierarchyDefinitionRequestById(UUID requestId);

  Future<List<OrganizationHierarchyDefinitionRequest>> getAllHierarchyDefinitionRequests(
      UUID organizationId);

  Future<OrganizationHierarchyDefinitionRequest> updateHierarchyDefinitionRequestStatus(
      UUID requestId, Status status, String remarks);

  Future<OrganizationUser> assignDeptAdmin(
      UUID organizationId, UUID assignerUserId, UUID targetUserId, UUID departmentNodeId);

  Future<DeptAdminRequest> requestDeptAdmin(UUID organizationId, UUID requesterUserId);

  Future<DeptAdminRequest> getDeptAdminRequestById(UUID requestId);

  Future<OrganizationUser> updateDeptAdminRequestStatus(
      UUID organizationId, UUID requestId, UUID approverUserId, Status status, String remarks);

  Future<Boolean> isAuthorizedOverNode(UUID organizationId, UUID userId, UUID targetNodeId);
}

