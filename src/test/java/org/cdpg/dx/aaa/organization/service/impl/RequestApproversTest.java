package org.cdpg.dx.aaa.organization.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.dao.DeptAdminRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationApprovalService;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.testutil.VertxFutureAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Who gets notified about a department-scoped request. */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
class RequestApproversTest {

  @Mock private OrganizationUserDAO orgUserDAO;
  @Mock private OrganizationHierarchyService hierarchyService;
  @Mock private DeptAdminRequestDAO deptAdminRequestDAO;
  @Mock private OrganizationApprovalService approvalService;
  @Mock private KeycloakUserService keycloakUserService;

  private DeptAdminServiceImpl service;

  private final UUID orgId = UUID.randomUUID();
  private final UUID transport = UUID.randomUUID();
  private final UUID metro = UUID.randomUUID();
  private final UUID roads = UUID.randomUUID();

  private final UUID orgAdmin = UUID.randomUUID();
  private final UUID transportAdmin = UUID.randomUUID();
  private final UUID metroAdmin = UUID.randomUUID();
  private final UUID roadsAdmin = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new DeptAdminServiceImpl(
        orgUserDAO, hierarchyService, deptAdminRequestDAO, approvalService, keycloakUserService);
  }

  private OrganizationUser admin(UUID userId, Role role, UUID adminOf) {
    return new OrganizationUser(UUID.randomUUID(), orgId, userId, "n", role, "t", "e", null,
        "a@b.c", adminOf, adminOf, null, null);
  }

  private void givenTreeAndAdmins() {
    when(hierarchyService.getOrganizationNodes(orgId)).thenReturn(Future.succeededFuture(List.of(
        new OrganizationHierarchyNode(transport, orgId, null, "Transport", 1, "transport", null, null),
        new OrganizationHierarchyNode(metro, orgId, transport, "Metro", 2, "transport.metro", null, null),
        new OrganizationHierarchyNode(roads, orgId, transport, "Roads", 2, "transport.roads", null, null))));
    when(orgUserDAO.getAllWithFilters(anyMap())).thenReturn(Future.succeededFuture(List.of(
        admin(orgAdmin, Role.ADMIN, null),
        admin(transportAdmin, Role.DEPT_ADMIN, transport),
        admin(metroAdmin, Role.DEPT_ADMIN, metro),
        admin(roadsAdmin, Role.DEPT_ADMIN, roads))));
  }

  @Test
  void memberRequestReachesOrgAdminAndAdminsOfTheDepartmentAndAbove(VertxTestContext ctx) {
    givenTreeAndAdmins();
    VertxFutureAssert.assertFutureSuccess(
        service.getApproverUserIds(orgId, metro, false), ctx,
        ids -> assertThat(ids).containsExactlyInAnyOrder(orgAdmin, transportAdmin, metroAdmin));
  }

  @Test
  void adminRequestSkipsTheDepartmentsOwnAdmin(VertxTestContext ctx) {
    givenTreeAndAdmins();
    VertxFutureAssert.assertFutureSuccess(
        service.getApproverUserIds(orgId, metro, true), ctx,
        ids -> assertThat(ids).containsExactlyInAnyOrder(orgAdmin, transportAdmin));
  }

  @Test
  void flatOrganisationReachesOnlyTheOrgAdmin(VertxTestContext ctx) {
    when(orgUserDAO.getAllWithFilters(anyMap())).thenReturn(Future.succeededFuture(List.of(
        admin(orgAdmin, Role.ADMIN, null))));
    VertxFutureAssert.assertFutureSuccess(
        service.getApproverUserIds(orgId, null, false), ctx,
        ids -> assertThat(ids).containsExactly(orgAdmin));
  }
}
