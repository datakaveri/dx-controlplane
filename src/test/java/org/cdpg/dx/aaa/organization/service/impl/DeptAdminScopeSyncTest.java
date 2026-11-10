package org.cdpg.dx.aaa.organization.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.dao.DeptAdminRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationJoinRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationApprovalService;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Dept-admin scopes are kept in step with the user's Keycloak scope attribute. */
@ExtendWith(MockitoExtension.class)
class DeptAdminScopeSyncTest {

  @Mock private OrganizationUserDAO organizationUserDAO;
  @Mock private OrganizationHierarchyService hierarchyService;
  @Mock private DeptAdminRequestDAO deptAdminRequestDAO;
  @Mock private OrganizationApprovalService approvalService;
  @Mock private KeycloakUserService keycloakUserService;
  @Mock private OrganizationJoinRequestDAO joinRequestDAO;
  @Mock private OrganizationDAO organizationDAO;
  @Mock private ItemService itemService;

  private DeptAdminServiceImpl deptAdminService;
  private OrganizationMembershipServiceImpl membershipService;

  private final UUID orgId = UUID.randomUUID();
  private final UUID orgAdminId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID nodeId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    deptAdminService =
        new DeptAdminServiceImpl(
            organizationUserDAO,
            hierarchyService,
            deptAdminRequestDAO,
            approvalService,
            keycloakUserService);
    membershipService =
        new OrganizationMembershipServiceImpl(
            joinRequestDAO,
            organizationUserDAO,
            organizationDAO,
            keycloakUserService,
            itemService,
            hierarchyService,
            approvalService);
  }

  private OrganizationUser member(UUID id, Role role, UUID adminOfNodeId) {
    return new OrganizationUser(
        UUID.randomUUID(), orgId, id, "name", role, "title", "emp", null, "a@b.c",
        nodeId, adminOfNodeId, null, null);
  }

  @Test
  @DisplayName("assigning a dept_admin publishes the dept scopes to Keycloak")
  void assignGrantsScopes() {
    OrganizationUser updated = member(userId, Role.DEPT_ADMIN, nodeId);
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, orgAdminId))
        .thenReturn(Future.succeededFuture(member(orgAdminId, Role.ADMIN, null)));
    when(hierarchyService.getNode(orgId, nodeId))
        .thenReturn(
            Future.succeededFuture(
                new OrganizationHierarchyNode(nodeId, orgId, null, "dept", 1, "dept", null, null)));
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, userId))
        .thenReturn(Future.succeededFuture(member(userId, Role.USER, null)));
    when(organizationUserDAO.update(anyMap(), anyMap()))
        .thenReturn(Future.succeededFuture(updated));
    when(keycloakUserService.addUserScopes(userId, Scopes.DEPT_ADMIN))
        .thenReturn(Future.succeededFuture(true));

    Future<OrganizationUser> result =
        deptAdminService.assignDeptAdmin(orgId, orgAdminId, userId, nodeId);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.result()).isEqualTo(updated);
    verify(keycloakUserService).addUserScopes(userId, Scopes.DEPT_ADMIN);
  }

  @Test
  @DisplayName("a failed Keycloak update fails the assignment so it can be retried")
  void assignFailsWhenKeycloakFails() {
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, orgAdminId))
        .thenReturn(Future.succeededFuture(member(orgAdminId, Role.ADMIN, null)));
    when(hierarchyService.getNode(orgId, nodeId))
        .thenReturn(
            Future.succeededFuture(
                new OrganizationHierarchyNode(nodeId, orgId, null, "dept", 1, "dept", null, null)));
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, userId))
        .thenReturn(Future.succeededFuture(member(userId, Role.USER, null)));
    when(organizationUserDAO.update(anyMap(), anyMap()))
        .thenReturn(Future.succeededFuture(member(userId, Role.DEPT_ADMIN, nodeId)));
    when(keycloakUserService.addUserScopes(userId, Scopes.DEPT_ADMIN))
        .thenReturn(Future.failedFuture(new RuntimeException("keycloak down")));

    assertThat(deptAdminService.assignDeptAdmin(orgId, orgAdminId, userId, nodeId).failed())
        .isTrue();
  }

  @Test
  @DisplayName("moving a user off dept_admin removes the dept scopes from Keycloak")
  void roleChangeRevokesScopes() {
    when(organizationUserDAO.update(anyMap(), anyMap()))
        .thenReturn(Future.succeededFuture(member(userId, Role.USER, null)));
    when(keycloakUserService.removeUserScopes(userId, Scopes.DEPT_ADMIN))
        .thenReturn(Future.succeededFuture(true));

    assertThat(membershipService.updateUserRole(orgId, userId, Role.USER).succeeded()).isTrue();
    verify(keycloakUserService).removeUserScopes(userId, Scopes.DEPT_ADMIN);
  }

  @Test
  @DisplayName("removing a member from the organisation removes the dept scopes from Keycloak")
  void memberRemovalRevokesScopes() {
    when(organizationUserDAO.deleteUserByOrgId(orgId, userId))
        .thenReturn(Future.succeededFuture(true));
    when(keycloakUserService.updateUserAttributes(eq(userId), anyMap()))
        .thenReturn(Future.succeededFuture(true));
    when(keycloakUserService.removeUserScopes(userId, Scopes.DEPT_ADMIN))
        .thenReturn(Future.succeededFuture(true));

    assertThat(membershipService.deleteOrganizationUser(orgId, userId).result()).isTrue();
    verify(keycloakUserService).removeUserScopes(userId, Scopes.DEPT_ADMIN);
  }

  @Test
  @DisplayName("a member that was not deleted leaves Keycloak untouched")
  void noRemovalNoRevoke() {
    when(organizationUserDAO.deleteUserByOrgId(orgId, userId))
        .thenReturn(Future.succeededFuture(false));

    assertThat(membershipService.deleteOrganizationUser(orgId, userId).result()).isFalse();
    verify(keycloakUserService, never()).removeUserScopes(any(), any());
  }
}
