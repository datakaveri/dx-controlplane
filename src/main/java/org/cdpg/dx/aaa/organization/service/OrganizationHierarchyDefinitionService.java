package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyDefinitionRequest;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;
import org.cdpg.dx.aaa.organization.models.Status;

/**
 * One-time "define the department hierarchy" request for an existing (flat)
 * organization -- approved/rejected by cos_admin as one atomic unit. On
 * approval the proposed departments are inserted as the organization's tree;
 * the org_admin needs no node assignment since they administer the whole
 * organization by role.
 */
public interface OrganizationHierarchyDefinitionService {

  Future<OrganizationHierarchyDefinitionRequest> createDefinitionRequest(
      UUID organizationId, List<ProposedDepartmentNode> departments);

  Future<OrganizationHierarchyDefinitionRequest> getDefinitionRequestById(UUID requestId);

  Future<List<OrganizationHierarchyDefinitionRequest>> getAllDefinitionRequests(UUID organizationId);

  Future<OrganizationHierarchyDefinitionRequest> updateDefinitionRequestStatus(
      UUID requestId, Status status, String remarks);
}
