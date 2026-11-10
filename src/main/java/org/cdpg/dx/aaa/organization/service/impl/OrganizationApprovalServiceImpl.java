package org.cdpg.dx.aaa.organization.service.impl;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import io.vertx.core.Future;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.OrganizationApprovalDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationHierarchyNodeDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationApproval;
import org.cdpg.dx.aaa.organization.models.OrganizationApprovalStage;
import org.cdpg.dx.aaa.organization.models.OrganizationApprovalType;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationApprovalPolicy;
import org.cdpg.dx.aaa.organization.service.OrganizationApprovalService;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Approves/rejects department-scoped requests (org joins, provider-role
 * requests, dept_admin self-requests) on behalf of whichever admin is
 * authorized for the request's target department.
 *
 * <p>Authorization rule: the org_admin (who administers the whole
 * organization by role, not through any department node) or any dept_admin
 * whose {@code adminOfNodeId} is an ancestor-or-self of the request's {@code
 * targetNodeId} may approve --
 * delegation is activated via ltree path containment, not a fixed level
 * number. Only ever invoked for organizations that have a department tree;
 * flat organizations keep the original direct org_admin-only flow untouched
 * (see the {@code hasHierarchy} branches in {@code
 * OrganizationMembershipServiceImpl}).
 */
public class OrganizationApprovalServiceImpl implements OrganizationApprovalService {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OrganizationApprovalServiceImpl.class);

  private final OrganizationApprovalDAO approvalDAO;
  private final OrganizationHierarchyNodeDAO hierarchyNodeDAO;
  private final OrganizationUserDAO organizationUserDAO;
  private final OrganizationApprovalPolicy approvalPolicy;
  private final OrganizationHierarchyService hierarchyService;

  public OrganizationApprovalServiceImpl(
      OrganizationApprovalDAO approvalDAO,
      OrganizationHierarchyNodeDAO hierarchyNodeDAO,
      OrganizationUserDAO organizationUserDAO,
      OrganizationApprovalPolicy approvalPolicy,
      OrganizationHierarchyService hierarchyService) {

    this.approvalDAO = approvalDAO;
    this.hierarchyNodeDAO = hierarchyNodeDAO;
    this.organizationUserDAO = organizationUserDAO;
    this.approvalPolicy = approvalPolicy;
    this.hierarchyService = hierarchyService;
  }

  @Override
  public Future<OrganizationApproval> createApproval(
      UUID organizationId,
      UUID requestId,
      OrganizationApprovalType approvalType,
      UUID targetNodeId) {

    return approvalDAO
        .getPendingApproval(requestId, approvalType)
        .compose(
            existing -> {
              if (existing != null) {
                return Future.succeededFuture(existing);
              }

              OrganizationApproval approval =
                  new OrganizationApproval(
                      null,
                      organizationId,
                      requestId,
                      approvalType,
                      // Vestigial since delegation moved to ancestor-or-self path
                      // containment -- kept only because the column is NOT NULL.
                      0,
                      null,
                      null,
                      0,
                      OrganizationApprovalStage.PENDING,
                      null,
                      null,
                      null);

              return approvalDAO.create(approval);
            });
  }

  @Override
  public Future<Boolean> canApprove(
      UUID organizationId,
      UUID requestId,
      OrganizationApprovalType approvalType,
      UUID targetNodeId,
      UUID approverUserId) {

    return getApproverContext(organizationId, approverUserId)
        .compose(
            context ->
                approvalDAO
                    .getPendingApproval(requestId, approvalType)
                    .compose(
                        approval -> {
                          if (approval == null) {
                            return Future.succeededFuture(false);
                          }

                          return canApproveTarget(
                              organizationId, context, approvalType, targetNodeId);
                        }))
        .recover(throwable -> Future.succeededFuture(false));
  }

  @Override
  public Future<OrganizationApproval> approve(
      UUID organizationId,
      UUID requestId,
      OrganizationApprovalType approvalType,
      UUID targetNodeId,
      UUID approverUserId,
      String remarks) {

    return processApproval(
        organizationId,
        requestId,
        approvalType,
        targetNodeId,
        approverUserId,
        remarks,
        OrganizationApprovalStage.APPROVED);
  }

  @Override
  public Future<OrganizationApproval> reject(
      UUID organizationId,
      UUID requestId,
      OrganizationApprovalType approvalType,
      UUID targetNodeId,
      UUID approverUserId,
      String remarks) {

    return processApproval(
        organizationId,
        requestId,
        approvalType,
        targetNodeId,
        approverUserId,
        remarks,
        OrganizationApprovalStage.REJECTED);
  }

  private Future<Boolean> canApproveTarget(
      UUID organizationId,
      ApproverContext context,
      OrganizationApprovalType approvalType,
      UUID targetNodeId) {

    // The org_admin administers the whole organization and is unrestricted.
    if (context.isOrgAdmin()) {
      return Future.succeededFuture(true);
    }

    if (!approvalPolicy.isDelegable(approvalType)) {
      return Future.succeededFuture(false);
    }

    if (targetNodeId == null) {
      // A department-scoped approval type with no target recorded on the
      // underlying request -- only the org_admin (handled above) may act.
      return Future.succeededFuture(false);
    }

    // A dept_admin self-request's target is the requester's own department --
    // that department's own (not-yet-existing) admin, i.e. the requester
    // themself, must not be able to self-approve, so this one case requires a
    // *strict* ancestor rather than ancestor-or-self.
    boolean strict = approvalType == OrganizationApprovalType.DEPT_ADMIN_SELF_REQUEST;

    return hierarchyService.isAncestorOrSelf(
        organizationId, context.node().id(), targetNodeId, strict);
  }

  private Future<OrganizationApproval> processApproval(
      UUID organizationId,
      UUID requestId,
      OrganizationApprovalType approvalType,
      UUID targetNodeId,
      UUID approverUserId,
      String remarks,
      OrganizationApprovalStage resultStatus) {

    return getApproverContext(organizationId, approverUserId)
        .compose(
            context ->
                approvalDAO
                    .getPendingApproval(requestId, approvalType)
                    .compose(
                        approval -> {
                          if (approval == null) {
                            return Future.failedFuture(
                                new DxNotFoundException(
                                    "No pending approval found for request: " + requestId));
                          }

                          return canApproveTarget(organizationId, context, approvalType, targetNodeId)
                              .compose(
                                  allowed -> {
                                    if (!allowed) {
                                      return Future.failedFuture(
                                          new DxForbiddenException(
                                              "User does not have permission to approve this"
                                                  + " request"));
                                    }

                                    Map<String, Object> updateFields = new HashMap<>();
                                    updateFields.put(
                                        Constants.PROCESSED_BY_USER_ID, approverUserId.toString());
                                    if (context.node() != null) {
                                      updateFields.put(
                                          Constants.PROCESSED_BY_NODE_ID,
                                          context.node().id().toString());
                                    }
                                    updateFields.put(
                                        Constants.PROCESSED_BY_LEVEL, context.level());
                                    updateFields.put(Constants.STATUS, resultStatus.getStatus());
                                    if (remarks != null) {
                                      updateFields.put(Constants.REMARKS, remarks);
                                    }
                                    updateFields.put(Constants.UPDATED_AT, LocalDateTime.now().format(FORMATTER));

                                    Map<String, Object> conditionMap =
                                        Map.of("id", approval.id().toString());

                                    return approvalDAO
                                        .update(conditionMap, updateFields)
                                        .compose(updated -> approvalDAO.get(approval.id()))
                                        .recover(
                                            throwable -> {
                                              LOGGER.debug(
                                                  "Failed to update approval: " + approval.id());
                                              return Future.failedFuture(
                                                  new DxValidationException(
                                                      throwable.getMessage()));
                                            });
                                  });
                        }));
  }

  private Future<ApproverContext> getApproverContext(UUID organizationId, UUID userId) {

    return organizationUserDAO
        .getByOrganizationIdAndUserId(organizationId, userId)
        .compose(
            user -> {
              if (user == null) {
                return Future.failedFuture(
                    new DxForbiddenException("User is not a member of the organisation"));
              }

              if (user.role() != Role.ADMIN && user.role() != Role.DEPT_ADMIN) {
                return Future.failedFuture(
                    new DxForbiddenException(
                        "Only an organisation admin or department admin can approve this"
                            + " request"));
              }

              if (user.role() == Role.ADMIN) {
                return Future.succeededFuture(new ApproverContext(user, null));
              }

              if (user.adminOfNodeId() == null) {
                return Future.failedFuture(
                    new DxForbiddenException("User does not administer any department"));
              }

              return hierarchyNodeDAO
                  .getByIdAndOrganizationId(user.adminOfNodeId(), organizationId)
                  .compose(
                      node -> {
                        if (node == null) {
                          return Future.failedFuture(
                              new DxNotFoundException(
                                  "Department administered by this user was not found"));
                        }

                        return Future.succeededFuture(new ApproverContext(user, node));
                      });
            });
  }

  @Override
  public Future<List<OrganizationApproval>> getApprovals(
      UUID requestId, OrganizationApprovalType approvalType) {

    return approvalDAO.getByRequest(requestId, approvalType);
  }

  /**
   * {@code node} is the department a dept_admin administers; null for the
   * org_admin, who sits above every department (level 0).
   */
  private record ApproverContext(OrganizationUser user, OrganizationHierarchyNode node) {

    boolean isOrgAdmin() {
      return node == null;
    }

    int level() {
      return node == null ? 0 : node.level();
    }
  }
}
