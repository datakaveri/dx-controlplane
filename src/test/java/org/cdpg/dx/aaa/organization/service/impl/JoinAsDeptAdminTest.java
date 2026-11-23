package org.cdpg.dx.aaa.organization.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.dao.OrganizationApprovalDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationHierarchyNodeDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationJoinRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationApproval;
import org.cdpg.dx.aaa.organization.models.OrganizationApprovalStage;
import org.cdpg.dx.aaa.organization.models.OrganizationApprovalType;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationApprovalService;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.testutil.VertxFutureAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Joining an organisation with departmentRole = dept_admin. */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
class JoinAsDeptAdminTest {

  private final UUID orgId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID nodeId = UUID.randomUUID();
  private final UUID requestId = UUID.randomUUID();
  private final UUID approverId = UUID.randomUUID();

  private static Organization org(UUID id) {
    return new Organization(
        id, "Org", "logo.png", "Private", "Tech", "https://example.com", "addr",
        "cert.pdf", "pan.pdf", "doc.pdf", "documents", null, null);
  }

  private OrganizationJoinRequest joinRequest(Role role, String purpose, UUID node, Status status) {
    return new OrganizationJoinRequest(
        requestId, orgId, userId, "Test User", status.getStatus(), "Lead", "EMP1",
        "lead@example.com", node, null, null, role, purpose);
  }

  @Nested
  class Membership {

    @Mock private OrganizationJoinRequestDAO joinRequestDAO;
    @Mock private OrganizationUserDAO orgUserDAO;
    @Mock private OrganizationDAO orgDAO;
    @Mock private KeycloakUserService keycloakUserService;
    @Mock private ItemService itemService;
    @Mock private OrganizationHierarchyService hierarchyService;
    @Mock private OrganizationApprovalService approvalService;

    private OrganizationMembershipServiceImpl service;

    @BeforeEach
    void setUp() {
      service =
          new OrganizationMembershipServiceImpl(
              joinRequestDAO, orgUserDAO, orgDAO, keycloakUserService, itemService,
              hierarchyService, approvalService);
    }

    @Test
    void flatOrganisationCannotBeJoinedAsDeptAdmin(VertxTestContext ctx) {
      when(orgDAO.get(orgId)).thenReturn(Future.succeededFuture(org(orgId)));
      when(hierarchyService.hasHierarchy(orgId)).thenReturn(Future.succeededFuture(false));

      // A flat organisation has no departments, so there is nothing to administer.
      OrganizationJoinRequest deptAdmin = joinRequest(Role.DEPT_ADMIN, "lead it", null, Status.PENDING);

      VertxFutureAssert.assertFutureFailure(
          service.joinOrganizationRequest(deptAdmin), ctx, err -> {
            assertThat(err).isInstanceOf(DxValidationException.class);
            verify(joinRequestDAO, never()).create(any());
          });
    }

    @Test
    void deptAdminJoinOpensStrictApproval(VertxTestContext ctx) {
      OrganizationJoinRequest request =
          joinRequest(Role.DEPT_ADMIN, "I lead this team", nodeId, Status.PENDING);
      when(orgDAO.get(orgId)).thenReturn(Future.succeededFuture(org(orgId)));
      when(hierarchyService.hasHierarchy(orgId)).thenReturn(Future.succeededFuture(true));
      when(hierarchyService.getNode(orgId, nodeId))
          .thenReturn(Future.succeededFuture(mock(OrganizationHierarchyNode.class)));
      when(joinRequestDAO.create(request)).thenReturn(Future.succeededFuture(request));
      when(approvalService.createApproval(
              orgId, requestId, OrganizationApprovalType.DEPT_ADMIN_JOIN, nodeId))
          .thenReturn(Future.succeededFuture(mock(OrganizationApproval.class)));

      VertxFutureAssert.assertFutureSuccess(
          service.joinOrganizationRequest(request), ctx,
          created -> assertThat(created.requestsDeptAdmin()).isTrue());
    }

    @Test
    void memberJoinKeepsRegularApproval(VertxTestContext ctx) {
      OrganizationJoinRequest request = joinRequest(Role.USER, null, nodeId, Status.PENDING);
      when(orgDAO.get(orgId)).thenReturn(Future.succeededFuture(org(orgId)));
      when(hierarchyService.hasHierarchy(orgId)).thenReturn(Future.succeededFuture(true));
      when(hierarchyService.getNode(orgId, nodeId))
          .thenReturn(Future.succeededFuture(mock(OrganizationHierarchyNode.class)));
      when(joinRequestDAO.create(request)).thenReturn(Future.succeededFuture(request));
      when(approvalService.createApproval(
              orgId, requestId, OrganizationApprovalType.ORGANIZATION_USER_JOIN, nodeId))
          .thenReturn(Future.succeededFuture(mock(OrganizationApproval.class)));

      VertxFutureAssert.assertFutureSuccess(
          service.joinOrganizationRequest(request), ctx,
          created -> assertThat(created.requestsDeptAdmin()).isFalse());
    }

    @Test
    void approvingDeptAdminJoinMakesUserAdminOfThatDepartment(VertxTestContext ctx) {
      OrganizationJoinRequest request =
          joinRequest(Role.DEPT_ADMIN, "I lead this team", nodeId, Status.PENDING);
      OrganizationApproval approved = mock(OrganizationApproval.class);
      when(approved.status()).thenReturn(OrganizationApprovalStage.APPROVED);

      when(joinRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(request));
      when(orgDAO.get(orgId)).thenReturn(Future.succeededFuture(org(orgId)));
      when(hierarchyService.hasHierarchy(orgId)).thenReturn(Future.succeededFuture(true));
      when(approvalService.approve(
              orgId, requestId, OrganizationApprovalType.DEPT_ADMIN_JOIN, nodeId, approverId, null))
          .thenReturn(Future.succeededFuture(approved));
      when(joinRequestDAO.update(any(), any())).thenReturn(Future.succeededFuture(request));
      when(orgUserDAO.create(any(OrganizationUser.class)))
          .thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));
      when(keycloakUserService.setOrganisationDetails(userId, orgId, "Org"))
          .thenReturn(Future.succeededFuture(true));
      when(keycloakUserService.addUserScopes(userId, Scopes.DEPT_ADMIN))
          .thenReturn(Future.succeededFuture(true));

      VertxFutureAssert.assertFutureSuccess(
          service.updateOrganizationJoinRequestStatus(requestId, Status.GRANTED, approverId), ctx,
          ok -> {
            assertThat(ok).isTrue();
            ArgumentCaptor<OrganizationUser> user = ArgumentCaptor.forClass(OrganizationUser.class);
            verify(orgUserDAO).create(user.capture());
            assertThat(user.getValue().role()).isEqualTo(Role.DEPT_ADMIN);
            assertThat(user.getValue().hierarchyNodeId()).isEqualTo(nodeId);
            assertThat(user.getValue().adminOfNodeId()).isEqualTo(nodeId);
            verify(keycloakUserService).addUserScopes(userId, Scopes.DEPT_ADMIN);
          });
    }

    @Test
    void approvingMemberJoinGrantsNoDeptScopes(VertxTestContext ctx) {
      OrganizationJoinRequest request = joinRequest(Role.USER, null, nodeId, Status.PENDING);
      when(joinRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(request));
      when(orgDAO.get(orgId)).thenReturn(Future.succeededFuture(org(orgId)));
      when(orgUserDAO.create(any(OrganizationUser.class)))
          .thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));
      when(keycloakUserService.setOrganisationDetails(userId, orgId, "Org"))
          .thenReturn(Future.succeededFuture(true));

      VertxFutureAssert.assertFutureSuccess(
          service.addUserToOrganizationFromRequest(requestId), ctx,
          ok -> {
            ArgumentCaptor<OrganizationUser> user = ArgumentCaptor.forClass(OrganizationUser.class);
            verify(orgUserDAO).create(user.capture());
            assertThat(user.getValue().role()).isEqualTo(Role.USER);
            assertThat(user.getValue().adminOfNodeId()).isNull();
            verify(keycloakUserService, never()).addUserScopes(any(), any());
          });
    }

    @Test
    void withdrawnJoinRequestCannotBeApproved(VertxTestContext ctx) {
      when(joinRequestDAO.get(requestId))
          .thenReturn(Future.succeededFuture(joinRequest(Role.USER, null, nodeId, Status.WITHDRAWN)));

      VertxFutureAssert.assertFutureFailure(
          service.updateOrganizationJoinRequestStatus(requestId, Status.GRANTED, approverId), ctx,
          err -> {
            assertThat(err).isInstanceOf(DxConflictException.class);
            verify(approvalService, never()).approve(any(), any(), any(), any(), any(), any());
            verify(orgUserDAO, never()).create(any());
          });
    }
  }

  @Nested
  class ApprovalRule {

    @Mock private OrganizationApprovalDAO approvalDAO;
    @Mock private OrganizationHierarchyNodeDAO nodeDAO;
    @Mock private OrganizationUserDAO orgUserDAO;
    @Mock private OrganizationHierarchyService hierarchyService;

    private OrganizationApprovalServiceImpl service;
    private final UUID approverNodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
      service =
          new OrganizationApprovalServiceImpl(
              approvalDAO, nodeDAO, orgUserDAO, new OrganizationApprovalPolicyImpl(),
              hierarchyService);
      OrganizationUser deptAdmin =
          new OrganizationUser(
              UUID.randomUUID(), orgId, approverId, "Approver", Role.DEPT_ADMIN, "Head", "E9",
              null, "head@example.com", approverNodeId, approverNodeId, null, null);
      when(orgUserDAO.getByOrganizationIdAndUserId(orgId, approverId))
          .thenReturn(Future.succeededFuture(deptAdmin));
      when(nodeDAO.getByIdAndOrganizationId(approverNodeId, orgId))
          .thenReturn(Future.succeededFuture(
              new OrganizationHierarchyNode(
                  approverNodeId, orgId, null, "Dept", 1, "dept", null, null)));
    }

    private void pendingApproval(OrganizationApprovalType type) {
      when(approvalDAO.getPendingApproval(requestId, type))
          .thenReturn(Future.succeededFuture(mock(OrganizationApproval.class)));
    }

    @Test
    void deptAdminJoinNeedsStrictAncestor(VertxTestContext ctx) {
      pendingApproval(OrganizationApprovalType.DEPT_ADMIN_JOIN);
      // The approver administers the very department being requested: not strictly above.
      when(hierarchyService.isAncestorOrSelf(orgId, approverNodeId, nodeId, true))
          .thenReturn(Future.succeededFuture(false));

      VertxFutureAssert.assertFutureSuccess(
          service.canApprove(
              orgId, requestId, OrganizationApprovalType.DEPT_ADMIN_JOIN, nodeId, approverId),
          ctx,
          allowed -> {
            assertThat(allowed).isFalse();
            verify(hierarchyService).isAncestorOrSelf(orgId, approverNodeId, nodeId, true);
          });
    }

    @Test
    void memberJoinAllowsOwnDepartmentAdmin(VertxTestContext ctx) {
      pendingApproval(OrganizationApprovalType.ORGANIZATION_USER_JOIN);
      when(hierarchyService.isAncestorOrSelf(orgId, approverNodeId, nodeId, false))
          .thenReturn(Future.succeededFuture(true));

      VertxFutureAssert.assertFutureSuccess(
          service.canApprove(
              orgId, requestId, OrganizationApprovalType.ORGANIZATION_USER_JOIN, nodeId, approverId),
          ctx,
          allowed -> {
            assertThat(allowed).isTrue();
            verify(hierarchyService, never())
                .isAncestorOrSelf(eq(orgId), eq(approverNodeId), eq(nodeId), eq(true));
          });
    }
  }
}
