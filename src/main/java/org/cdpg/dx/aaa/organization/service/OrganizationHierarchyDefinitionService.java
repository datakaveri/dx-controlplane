package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import java.util.List;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyDefinitionRequest;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;
import org.cdpg.dx.aaa.organization.models.Status;

/**
 * Requests to add departments to an organization -- approved/rejected by
 * cos_admin as one atomic unit. The first request defines a flat
 * organization's departments; later ones add top-level departments or
 * departments under an existing one. The org_admin needs no node assignment
 * since they administer the whole organization by role.
 */
public interface OrganizationHierarchyDefinitionService {

  /** {@code parentNodeId}: existing department to add under; null for top-level departments. */
  Future<OrganizationHierarchyDefinitionRequest> createDefinitionRequest(
      UUID organizationId, UUID parentNodeId, List<ProposedDepartmentNode> departments);

  /** cos_admin queue across organizations (filter by status, organization). */
  Future<PaginatedResult<OrganizationHierarchyDefinitionRequest>> getDefinitionRequests(
      PaginatedRequest request);

  Future<OrganizationHierarchyDefinitionRequest> getDefinitionRequestById(UUID requestId);

  Future<List<OrganizationHierarchyDefinitionRequest>> getAllDefinitionRequests(UUID organizationId);

  Future<OrganizationHierarchyDefinitionRequest> updateDefinitionRequestStatus(
      UUID requestId, Status status, String remarks);
}
