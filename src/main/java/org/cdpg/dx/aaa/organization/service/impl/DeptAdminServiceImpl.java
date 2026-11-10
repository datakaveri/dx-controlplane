package org.cdpg.dx.aaa.organization.service.impl;

import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.DeptAdminRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.DeptAdminRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationApprovalType;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.DeptAdminService;
import org.cdpg.dx.aaa.organization.service.OrganizationApprovalService;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class DeptAdminServiceImpl implements DeptAdminService {

  private final OrganizationUserDAO organizationUserDAO;
  private final OrganizationHierarchyService hierarchyService;
  private final DeptAdminRequestDAO deptAdminRequestDAO;
  private final OrganizationApprovalService organizationApprovalService;
  private final KeycloakUserService keycloakUserService;

  public DeptAdminServiceImpl(
      OrganizationUserDAO organizationUserDAO,
      OrganizationHierarchyService hierarchyService,
      DeptAdminRequestDAO deptAdminRequestDAO,
      OrganizationApprovalService organizationApprovalService,
      KeycloakUserService keycloakUserService) {
    this.organizationUserDAO = organizationUserDAO;
    this.hierarchyService = hierarchyService;
    this.deptAdminRequestDAO = deptAdminRequestDAO;
    this.organizationApprovalService = organizationApprovalService;
    this.keycloakUserService = keycloakUserService;
  }

  @Override
  public Future<Boolean> isAuthorizedOverNode(
      UUID organizationId, UUID userId, UUID targetNodeId) {

    return organizationUserDAO
        .getByOrganizationIdAndUserId(organizationId, userId)
        .compose(
            adminUser -> {
              if (adminUser == null) {
                return Future.succeededFuture(false);
              }
              return isAuthorizedOverNode(organizationId, adminUser, targetNodeId);
            });
  }

  /** True if {@code adminUser} administers {@code targetNodeId} or an ancestor of it. */
  private Future<Boolean> isAuthorizedOverNode(
      UUID organizationId, OrganizationUser adminUser, UUID targetNodeId) {

    // The org_admin administers the whole organization -- unrestricted.
    if (adminUser.role() == Role.ADMIN) {
      return Future.succeededFuture(true);
    }

    if (adminUser.role() != Role.DEPT_ADMIN || adminUser.adminOfNodeId() == null) {
      return Future.succeededFuture(false);
    }

    return hierarchyService.isAncestorOrSelf(
        organizationId, adminUser.adminOfNodeId(), targetNodeId, false);
  }

  @Override
  public Future<OrganizationUser> assignDeptAdmin(
      UUID organizationId, UUID assignerUserId, UUID targetUserId, UUID departmentNodeId) {

    return organizationUserDAO
        .getByOrganizationIdAndUserId(organizationId, assignerUserId)
        .compose(
            assigner -> {
              if (assigner == null) {
                return Future.failedFuture(
                    new DxForbiddenException("Assigner is not a member of this organisation"));
              }

              return isAuthorizedOverNode(organizationId, assigner, departmentNodeId);
            })
        .compose(
            authorized -> {
              if (!authorized) {
                return Future.failedFuture(
                    new DxForbiddenException(
                        "Only the org_admin or an ancestor dept_admin may assign this"
                            + " department's admin"));
              }

              return hierarchyService.getNode(organizationId, departmentNodeId);
            })
        .compose(
            departmentNode -> {
              if (departmentNode == null) {
                return Future.failedFuture(new DxNotFoundException("Department not found"));
              }

              return organizationUserDAO.getByOrganizationIdAndUserId(
                  organizationId, targetUserId);
            })
        .compose(
            target -> {
              if (target == null) {
                return Future.failedFuture(
                    new DxNotFoundException("Target user is not a member of this organisation"));
              }

              Map<String, Object> conditionMap =
                  Map.of(
                      Constants.ORGANIZATION_ID, organizationId.toString(),
                      Constants.USER_ID, targetUserId.toString());

              Map<String, Object> updateFields =
                  Map.of(
                      Constants.ROLE, Role.DEPT_ADMIN.getRoleName(),
                      Constants.ADMIN_OF_NODE_ID, departmentNodeId.toString());

              return organizationUserDAO
                  .update(conditionMap, updateFields)
                  .compose(updated -> grantDeptAdminScopes(targetUserId, updated));
            });
  }

  @Override
  public Future<DeptAdminRequest> requestDeptAdmin(UUID organizationId, UUID requesterUserId) {

    return organizationUserDAO
        .getByOrganizationIdAndUserId(organizationId, requesterUserId)
        .compose(
            requester -> {
              if (requester == null) {
                return Future.failedFuture(
                    new DxForbiddenException("Requester is not a member of this organisation"));
              }

              if (requester.hierarchyNodeId() == null) {
                return Future.failedFuture(
                    new DxValidationException(
                        "User is not assigned to a department in this organisation"));
              }

              if (requester.role() == Role.DEPT_ADMIN
                  && requester.adminOfNodeId() != null
                  && requester.adminOfNodeId().equals(requester.hierarchyNodeId())) {
                return Future.failedFuture(
                    new DxConflictException("User is already the admin of this department"));
              }

              return deptAdminRequestDAO
                  .getAllWithFilters(
                      Map.of(
                          Constants.USER_ID, requesterUserId.toString(),
                          Constants.STATUS, Status.PENDING.getStatus()))
                  .compose(
                      (List<DeptAdminRequest> pending) -> {
                        if (!pending.isEmpty()) {
                          return Future.failedFuture(
                              new DxConflictException(
                                  "A dept_admin request is already pending for this user"));
                        }

                        DeptAdminRequest request =
                            new DeptAdminRequest(
                                null,
                                organizationId,
                                requesterUserId,
                                requester.hierarchyNodeId(),
                                Status.PENDING.getStatus(),
                                null,
                                null);

                        return deptAdminRequestDAO.create(request);
                      });
            })
        .compose(
            created ->
                organizationApprovalService
                    .createApproval(
                        organizationId,
                        created.id(),
                        OrganizationApprovalType.DEPT_ADMIN_SELF_REQUEST,
                        created.requestedNodeId())
                    .map(ignored -> created));
  }

  @Override
  public Future<DeptAdminRequest> getDeptAdminRequestById(UUID requestId) {
    return deptAdminRequestDAO.get(requestId);
  }

  @Override
  public Future<OrganizationUser> updateDeptAdminRequestStatus(
      UUID organizationId, UUID requestId, UUID approverUserId, Status status, String remarks) {

    return deptAdminRequestDAO
        .get(requestId)
        .compose(
            request -> {
              if (request == null || !request.organizationId().equals(organizationId)) {
                return Future.failedFuture(
                    new DxNotFoundException("Dept_admin request not found"));
              }

              if (!Status.PENDING.getStatus().equalsIgnoreCase(request.status())) {
                return Future.failedFuture(
                    new DxConflictException("Request has already been " + request.status()));
              }

              Future<?> approvalFuture =
                  status == Status.GRANTED
                      ? organizationApprovalService.approve(
                          organizationId,
                          requestId,
                          OrganizationApprovalType.DEPT_ADMIN_SELF_REQUEST,
                          request.requestedNodeId(),
                          approverUserId,
                          remarks)
                      : organizationApprovalService.reject(
                          organizationId,
                          requestId,
                          OrganizationApprovalType.DEPT_ADMIN_SELF_REQUEST,
                          request.requestedNodeId(),
                          approverUserId,
                          remarks);

              return approvalFuture.compose(
                  approval -> {
                    Map<String, Object> conditionMap = Map.of("id", requestId.toString());
                    Map<String, Object> updateFields =
                        Map.of(Constants.STATUS, status.getStatus());

                    return deptAdminRequestDAO
                        .update(conditionMap, updateFields)
                        .compose(
                            updatedRequest -> {
                              if (status != Status.GRANTED) {
                                return Future.succeededFuture((OrganizationUser) null);
                              }

                              Map<String, Object> userConditionMap =
                                  Map.of(
                                      Constants.ORGANIZATION_ID, organizationId.toString(),
                                      Constants.USER_ID, request.userId().toString());

                              Map<String, Object> userUpdateFields =
                                  Map.of(
                                      Constants.ROLE, Role.DEPT_ADMIN.getRoleName(),
                                      Constants.ADMIN_OF_NODE_ID,
                                          request.requestedNodeId().toString());

                              return organizationUserDAO
                                  .update(userConditionMap, userUpdateFields)
                                  .compose(
                                      updated -> grantDeptAdminScopes(request.userId(), updated));
                            });
                  });
            });
  }

  /**
   * Publishes the dept scopes to the user's Keycloak scope attribute so they appear in their
   * token (the UI gates features on it). The backend itself authorizes dept_admins from the
   * database -- see DeptAdminAccessHandler.
   */
  private Future<OrganizationUser> grantDeptAdminScopes(UUID userId, OrganizationUser updated) {
    return keycloakUserService.addUserScopes(userId, Scopes.DEPT_ADMIN).map(updated);
  }
}
