package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.DeptAdminRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Status;

public interface DeptAdminService {

  /**
   * Org_admin (or an ancestor dept_admin) directly assigns {@code
   * targetUserId} as dept_admin of {@code departmentNodeId}. {@code
   * targetUserId} must already be a member of the organization.
   */
  Future<OrganizationUser> assignDeptAdmin(
      UUID organizationId, UUID assignerUserId, UUID targetUserId, UUID departmentNodeId);

  /** A member requests to become dept_admin of their own department. */
  Future<DeptAdminRequest> requestDeptAdmin(UUID organizationId, UUID requesterUserId);

  Future<DeptAdminRequest> getDeptAdminRequestById(UUID requestId);

  Future<OrganizationUser> updateDeptAdminRequestStatus(
      UUID organizationId, UUID requestId, UUID approverUserId, Status status, String remarks);

  /**
   * True if {@code userId} is the org_admin, or a dept_admin whose
   * administered department is an ancestor-or-self of {@code targetNodeId}.
   * Shared by dept_admin assignment and delegated department creation.
   */
  Future<Boolean> isAuthorizedOverNode(UUID organizationId, UUID userId, UUID targetNodeId);
}
