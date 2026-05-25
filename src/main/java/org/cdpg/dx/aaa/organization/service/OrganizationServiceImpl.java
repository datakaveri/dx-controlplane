package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAOFactory;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.service.impl.OrganizationLifecycleServiceImpl;
import org.cdpg.dx.aaa.organization.service.impl.OrganizationMembershipServiceImpl;
import org.cdpg.dx.aaa.organization.service.impl.ProviderRoleServiceImpl;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.List;
import java.util.UUID;

public class OrganizationServiceImpl implements OrganizationService {

  private final OrganizationLifecycleService lifecycle;
  private final OrganizationMembershipService membership;
  private final ProviderRoleService providerRole;

  public OrganizationServiceImpl(
      OrganizationLifecycleService lifecycle,
      OrganizationMembershipService membership,
      ProviderRoleService providerRole) {
    this.lifecycle = lifecycle;
    this.membership = membership;
    this.providerRole = providerRole;
  }

  public OrganizationServiceImpl(OrganizationDAOFactory factory, KeycloakUserService keycloakUserService, ItemService itemService) {
    OrganizationLifecycleServiceImpl lifecycleImpl = new OrganizationLifecycleServiceImpl(
      factory.organizationCreateRequest(), factory.organizationDAO(), factory.organizationUserDAO(), keycloakUserService);
    OrganizationMembershipServiceImpl membershipImpl = new OrganizationMembershipServiceImpl(
      factory.organizationJoinRequestDAO(), factory.organizationUserDAO(), factory.organizationDAO(), keycloakUserService, itemService);
    ProviderRoleServiceImpl providerRoleImpl = new ProviderRoleServiceImpl(
      factory.providerRoleRequestDAO(), factory.organizationUserDAO(), keycloakUserService);

    this.lifecycle = lifecycleImpl;
    this.membership = membershipImpl;
    this.providerRole = providerRoleImpl;
  }

  // ============ Organization Lifecycle (create requests, CRUD on orgs) ============

  @Override
  public Future<OrganizationCreateRequest> createOrganizationRequest(OrganizationCreateRequest request) {
    return lifecycle.createOrganizationRequest(request);
  }

  @Override
  public Future<OrganizationCreateRequest> getOrganizationCreateRequestById(UUID requestId) {
    return lifecycle.getOrganizationCreateRequestById(requestId);
  }

  @Override
  public Future<List<OrganizationCreateRequest>> getOrganizationCreateRequestsByUserId(UUID userId) {
    return lifecycle.getOrganizationCreateRequestsByUserId(userId);
  }

  @Override
  public Future<List<OrganizationCreateRequest>> getAllPendingGrantedOrganizationCreateRequests() {
    return lifecycle.getAllPendingGrantedOrganizationCreateRequests();
  }

  @Override
  public Future<PaginatedResult<OrganizationCreateRequest>> getAllOrganizationCreateRequests(PaginatedRequest request) {
    return lifecycle.getAllOrganizationCreateRequests(request);
  }

  @Override
  public Future<Boolean> updateOrganizationCreateRequestStatus(UUID requestId, Status status) {
    return lifecycle.updateOrganizationCreateRequestStatus(requestId, status);
  }

  public Future<Boolean> createOrganizationFromRequest(UUID requestId) {
    return lifecycle.createOrganizationFromRequest(requestId);
  }

  @Override
  public Future<Organization> getOrganizationById(UUID orgId) {
    return lifecycle.getOrganizationById(orgId);
  }

  @Override
  public Future<List<Organization>> getOrganizations() {
    return lifecycle.getOrganizations();
  }

  @Override
  public Future<PaginatedResult<Organization>> getOrganizations(PaginatedRequest paginatedRequest) {
    return lifecycle.getOrganizations(paginatedRequest);
  }

  @Override
  public Future<Organization> getOrganizationByName(String orgName) {
    return lifecycle.getOrganizationByName(orgName);
  }

  @Override
  public Future<Organization> updateOrganizationById(UUID orgId, UpdateOrgDTO updateOrgDTO) {
    return lifecycle.updateOrganizationById(orgId, updateOrgDTO);
  }

  @Override
  public Future<Boolean> deleteOrganization(UUID orgId) {
    return lifecycle.deleteOrganization(orgId);
  }

  @Override
  public Future<Boolean> deleteOrganizationRequestById(UUID requestId) {
    return lifecycle.deleteOrganizationRequestById(requestId);
  }

  // ============ Organization Membership (join requests, users) ============

  @Override
  public Future<OrganizationJoinRequest> joinOrganizationRequest(OrganizationJoinRequest organizationJoinRequest) {
    return membership.joinOrganizationRequest(organizationJoinRequest);
  }

  @Override
  public Future<OrganizationJoinRequest> getOrganizationJoinRequestById(UUID requestId) {
    return membership.getOrganizationJoinRequestById(requestId);
  }

  @Override
  public Future<PaginatedResult<OrganizationJoinRequest>> getOrganizationPendingJoinRequests(PaginatedRequest paginatedRequest) {
    return membership.getOrganizationPendingJoinRequests(paginatedRequest);
  }

  @Override
  public Future<Boolean> updateOrganizationJoinRequestStatus(UUID requestId, Status status) {
    return membership.updateOrganizationJoinRequestStatus(requestId, status);
  }

  @Override
  public Future<OrganizationJoinRequest> withdrawJoinRequest(UUID userId, UUID requestId) {
    return membership.withdrawJoinRequest(userId, requestId);
  }

  public Future<Boolean> addUserToOrganizationFromRequest(UUID requestId) {
    return membership.addUserToOrganizationFromRequest(requestId);
  }

  @Override
  public Future<List<OrganizationJoinRequest>> getOrganizationJoinRequestsByUser(UUID userId) {
    return membership.getOrganizationJoinRequestsByUser(userId);
  }

  @Override
  public Future<List<OrganizationJoinRequest>> getOrganizationJoinRequestsByOrgId(UUID orgId) {
    return membership.getOrganizationJoinRequestsByOrgId(orgId);
  }

  @Override
  public Future<List<OrganizationJoinRequest>> getAllOrganizationJoinRequests() {
    return membership.getAllOrganizationJoinRequests();
  }

  @Override
  public Future<PaginatedResult<OrganizationUser>> getOrganizationUsers(PaginatedRequest paginatedRequest) {
    return membership.getOrganizationUsers(paginatedRequest);
  }

  @Override
  public Future<Boolean> updateUserRole(UUID orgId, UUID userId, Role role) {
    return membership.updateUserRole(orgId, userId, role);
  }

  @Override
  public Future<Boolean> deleteOrganizationUser(UUID orgId, UUID userId) {
    return membership.deleteOrganizationUser(orgId, userId);
  }

  @Override
  public Future<Boolean> deleteProviderUser(UUID userId, UUID orgAdminId, UUID orgId) {
    return membership.deleteProviderUser(userId, orgAdminId, orgId);
  }

  @Override
  public Future<OrganizationUser> getOrganizationUserInfo(UUID userId) {
    return membership.getOrganizationUserInfo(userId);
  }

  @Override
  public Future<OrganizationUser> getOrganisationUserByUserId(UUID userId) {
    return membership.getOrganisationUserByUserId(userId);
  }

  @Override
  public Future<List<OrganizationUser>> getOrganisationAdminId(UUID orgId) {
    return membership.getOrganisationAdminId(orgId);
  }

  @Override
  public Future<Boolean> isOrgAdmin(UUID orgid, UUID userid) {
    return membership.isOrgAdmin(orgid, userid);
  }

  @Override
  public Future<UUID> getUserOrgAdminId(UUID orgId) {
    return membership.getUserOrgAdminId(orgId);
  }

  @Override
  public Future<Boolean> deleteOrganizationJoinRequest(UUID orgId, UUID userId) {
    return membership.deleteOrganizationJoinRequest(orgId, userId);
  }

  @Override
  public Future<Boolean> deleteOrganizationJoinRequestById(UUID requestId) {
    return membership.deleteOrganizationJoinRequestById(requestId);
  }

  @Override
  public Future<List<JsonObject>> enrichWithUserInfo(List<JsonObject> computeReqs) {
    return membership.enrichWithUserInfo(computeReqs);
  }

  // ============ Provider Role (provider requests, role management) ============

  @Override
  public Future<ProviderRoleRequest> createProviderRequest(ProviderRoleRequest providerRoleRequest) {
    return providerRole.createProviderRequest(providerRoleRequest);
  }

  @Override
  public Future<Boolean> updateProviderRequestStatus(UUID requestId, Status status) {
    return providerRole.updateProviderRequestStatus(requestId, status);
  }

  @Override
  public Future<List<ProviderRoleRequest>> getAllPendingProviderRoleRequests(UUID orgId) {
    return providerRole.getAllPendingProviderRoleRequests(orgId);
  }

  @Override
  public Future<PaginatedResult<ProviderRoleRequest>> getAllPendingProviderRoleRequests(PaginatedRequest paginatedRequest) {
    return providerRole.getAllPendingProviderRoleRequests(paginatedRequest);
  }

  @Override
  public Future<Boolean> hasPendingProviderRole(UUID userId, UUID orgId) {
    return providerRole.hasPendingProviderRole(userId, orgId);
  }

  @Override
  public Future<Boolean> createProviderRole(ProviderRoleRequest providerRoleRequest) {
    return providerRole.createProviderRole(providerRoleRequest);
  }

  @Override
  public Future<ProviderRoleRequest> getProviderRequestById(UUID requestId) {
    return providerRole.getProviderRequestById(requestId);
  }

  @Override
  public Future<ProviderRoleRequest> getProviderRoleRequestByUserId(UUID userId) {
    return providerRole.getProviderRoleRequestByUserId(userId);
  }

  @Override
  public Future<Boolean> deleteProviderRoleRequest(UUID orgId, UUID userId) {
    return providerRole.deleteProviderRoleRequest(orgId, userId);
  }

  @Override
  public Future<Boolean> deleteProviderRoleRequestById(UUID id) {
    return providerRole.deleteProviderRoleRequestById(id);
  }

  @Override
  public Future<Boolean> hasPendingPlatformProviderRole(UUID userId) {
    return providerRole.hasPendingPlatformProviderRole(userId);
  }

  @Override
  public Future<PaginatedResult<ProviderRoleRequest>> getAllPlatformProviderRequests(PaginatedRequest request) {
    return providerRole.getAllPlatformProviderRequests(request);
  }

  @Override
  public Future<ProviderRoleRequest> getPlatformProviderRoleRequestByUserId(UUID userId) {
    return providerRole.getPlatformProviderRoleRequestByUserId(userId);
  }
}
