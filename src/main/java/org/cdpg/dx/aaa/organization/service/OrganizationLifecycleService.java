package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.List;
import java.util.UUID;

public interface OrganizationLifecycleService {

  Future<OrganizationCreateRequest> createOrganizationRequest(OrganizationCreateRequest request);

  Future<OrganizationCreateRequest> getOrganizationCreateRequestById(UUID requestId);

  Future<List<OrganizationCreateRequest>> getOrganizationCreateRequestsByUserId(UUID userId);

  Future<List<OrganizationCreateRequest>> getAllPendingGrantedOrganizationCreateRequests();

  Future<PaginatedResult<OrganizationCreateRequest>> getAllOrganizationCreateRequests(PaginatedRequest request);

  Future<Boolean> updateOrganizationCreateRequestStatus(UUID requestId, Status status);

  Future<Boolean> createOrganizationFromRequest(UUID requestId);

  Future<Organization> getOrganizationById(UUID orgId);

  Future<List<Organization>> getOrganizations();

  Future<PaginatedResult<Organization>> getOrganizations(PaginatedRequest paginatedRequest);

  Future<Organization> getOrganizationByName(String orgName);

  Future<Organization> updateOrganizationById(UUID orgId, UpdateOrgDTO updateOrgDTO);

  Future<Boolean> deleteOrganization(UUID orgId);

  Future<Boolean> deleteOrganizationRequestById(UUID requestId);
}
