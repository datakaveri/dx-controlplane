package org.cdpg.dx.aaa.organization.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link DeptAdminAccessHandler#departmentNodeIds}: the Manage-users department filter. */
@ExtendWith(MockitoExtension.class)
class DepartmentNodeIdsTest {

  @Mock private OrganizationUserDAO organizationUserDAO;
  @Mock private OrganizationHierarchyService hierarchyService;
  @Mock private RoutingContext ctx;

  private DeptAdminAccessHandler guard;
  private final MultiMap params = MultiMap.caseInsensitiveMultiMap();

  private final UUID orgId = UUID.randomUUID();
  private final UUID adminNode = UUID.randomUUID();
  private final UUID child = UUID.randomUUID();
  private final UUID grandChild = UUID.randomUUID();
  private final UUID outside = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    guard = new DeptAdminAccessHandler(organizationUserDAO, hierarchyService);
    when(ctx.queryParams()).thenReturn(params);
  }

  private void asDeptAdmin() {
    when(ctx.get(DeptAdminAccessHandler.DEPT_ADMIN_SCOPE_KEY))
        .thenReturn(
            new DeptAdminScope(orgId, UUID.randomUUID(), adminNode,
                List.of(adminNode, child, grandChild)));
  }

  private static <T> T result(Future<T> future) {
    assertThat(future.succeeded()).as("future succeeded: %s", future.cause()).isTrue();
    return future.result();
  }

  private static Throwable failure(Future<?> future) {
    assertThat(future.failed()).isTrue();
    return future.cause();
  }

  @Test
  void orgAdminWithoutDepartmentIsUnrestricted() {
    assertThat(result(guard.departmentNodeIds(ctx, orgId))).isNull();
    verify(hierarchyService, never()).getDescendantNodeIds(any(), any());
  }

  @Test
  void orgAdminDepartmentIncludesSubDepartments() {
    params.add("departmentId", child.toString());
    when(hierarchyService.getDescendantNodeIds(orgId, child))
        .thenReturn(Future.succeededFuture(List.of(child, grandChild)));

    assertThat(result(guard.departmentNodeIds(ctx, orgId)))
        .containsExactly(child.toString(), grandChild.toString());
  }

  @Test
  void deptAdminWithoutDepartmentGetsTheirSubtree() {
    asDeptAdmin();
    assertThat(result(guard.departmentNodeIds(ctx, orgId)))
        .containsExactlyInAnyOrder(adminNode.toString(), child.toString(), grandChild.toString());
  }

  @Test
  void deptAdminCannotPickADepartmentOutsideTheirSubtree() {
    asDeptAdmin();
    params.add("departmentId", outside.toString());

    assertThat(failure(guard.departmentNodeIds(ctx, orgId)))
        .isInstanceOf(DxForbiddenException.class);
    verify(hierarchyService, never()).getDescendantNodeIds(any(), any());
  }

  @Test
  void departmentIdAndDepartmentScopeCombine() {
    asDeptAdmin();
    params.add("departmentId", adminNode.toString()).add("departmentScope", "own");
    when(hierarchyService.getDescendantNodeIds(orgId, adminNode))
        .thenReturn(Future.succeededFuture(List.of(adminNode, child, grandChild)));

    assertThat(result(guard.departmentNodeIds(ctx, orgId))).containsExactly(adminNode.toString());
  }

  @Test
  void noOverlapMatchesNothingRatherThanEverything() {
    asDeptAdmin();
    params.add("departmentId", child.toString()).add("departmentScope", "own");
    when(hierarchyService.getDescendantNodeIds(orgId, child))
        .thenReturn(Future.succeededFuture(List.of(child, grandChild)));

    assertThat(result(guard.departmentNodeIds(ctx, orgId)))
        .containsExactly(DeptAdminScope.NO_DEPARTMENT);
  }

  @Test
  void invalidDepartmentIdIsABadRequest() {
    params.add("departmentId", "not-a-uuid");
    assertThat(failure(guard.departmentNodeIds(ctx, orgId)))
        .isInstanceOf(DxBadRequestException.class);
  }
}
