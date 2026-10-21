package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.OrganizationApproval;
import org.cdpg.dx.aaa.organization.models.OrganizationApprovalType;

public interface OrganizationApprovalService {

  /**
   * @param targetNodeId the department the underlying request is scoped to
   *     (e.g. a join request's requestedNodeId). May be null only for
   *     approval types that are not department-scoped.
   */
  Future<OrganizationApproval> createApproval(
      UUID organizationId,
      UUID requestId,
      OrganizationApprovalType approvalType,
      UUID targetNodeId);

  Future<OrganizationApproval> approve(
      UUID organizationId,
      UUID requestId,
      OrganizationApprovalType approvalType,
      UUID targetNodeId,
      UUID approverUserId,
      String remarks);

  Future<OrganizationApproval> reject(
      UUID organizationId,
      UUID requestId,
      OrganizationApprovalType approvalType,
      UUID targetNodeId,
      UUID approverUserId,
      String remarks);

  Future<Boolean> canApprove(
      UUID organizationId,
      UUID requestId,
      OrganizationApprovalType approvalType,
      UUID targetNodeId,
      UUID approverUserId);

  Future<List<OrganizationApproval>> getApprovals(
      UUID requestId, OrganizationApprovalType approvalType);
}
