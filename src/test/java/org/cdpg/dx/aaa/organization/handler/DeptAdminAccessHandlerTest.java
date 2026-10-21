package org.cdpg.dx.aaa.organization.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.auth.model.Scopes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeptAdminAccessHandlerTest {

  @Mock private OrganizationUserDAO organizationUserDAO;
  @Mock private OrganizationHierarchyService hierarchyService;
  @Mock private RoutingContext ctx;
  @Mock private Handler<RoutingContext> scopeCheck;

  private DeptAdminAccessHandler guard;

  private final UUID orgId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID adminNode = UUID.randomUUID();
  private final UUID childNode = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    guard = new DeptAdminAccessHandler(organizationUserDAO, hierarchyService);
  }

  private JsonObject principal;

  private void givenPrincipal(JsonObject extraClaims) {
    principal =
        new JsonObject()
            .put("sub", userId.toString())
            .put("scopes", new JsonArray().add("data-access"))
            .mergeIn(extraClaims);
    when(ctx.user()).thenReturn(User.create(principal));
  }

  private OrganizationUser member(Role role, UUID adminOfNodeId) {
    return new OrganizationUser(
        UUID.randomUUID(), orgId, userId, "name", role, "title", "emp", null, "a@b.c",
        adminNode, adminOfNodeId, null, null);
  }

  @Test
  @DisplayName("a dept_admin is granted the dept scopes with their department subtree published")
  void deptAdminGrantedScopes() {
    givenPrincipal(new JsonObject().put("organisation_id", orgId.toString()));
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, userId))
        .thenReturn(Future.succeededFuture(member(Role.DEPT_ADMIN, adminNode)));
    when(hierarchyService.getDescendantNodeIds(orgId, adminNode))
        .thenReturn(Future.succeededFuture(List.of(adminNode, childNode)));

    guard.withDeptScopes(scopeCheck).handle(ctx);

    ArgumentCaptor<DeptAdminScope> scope = ArgumentCaptor.forClass(DeptAdminScope.class);
    verify(ctx).put(eq(DeptAdminAccessHandler.DEPT_ADMIN_SCOPE_KEY), scope.capture());
    verify(scopeCheck).handle(ctx);
    assertThat(scope.getValue().organizationId()).isEqualTo(orgId);
    assertThat(scope.getValue().nodeIds()).containsExactly(adminNode, childNode);
    assertThat(principal.getJsonArray("scopes").getList())
        .containsExactlyInAnyOrderElementsOf(
            Stream.concat(
                    Stream.of("data-access"), Scopes.DEPT_ADMIN.stream())
                .toList());
  }

  @Test
  @DisplayName("a plain member reaches the scope check with only their token's scopes")
  void memberNotGranted() {
    givenPrincipal(new JsonObject().put("organisation_id", orgId.toString()));
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, userId))
        .thenReturn(Future.succeededFuture(member(Role.USER, null)));

    guard.withDeptScopes(scopeCheck).handle(ctx);

    verify(scopeCheck).handle(ctx);
    verify(ctx, never()).put(any(), any());
    assertThat(principal.getJsonArray("scopes").getList()).containsExactly("data-access");
  }

  @Test
  @DisplayName("an org_admin is not granted the dept scopes")
  void orgAdminNotGranted() {
    givenPrincipal(new JsonObject().put("organisation_id", orgId.toString()));
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, userId))
        .thenReturn(Future.succeededFuture(member(Role.ADMIN, null)));

    guard.withDeptScopes(scopeCheck).handle(ctx);

    verify(scopeCheck).handle(ctx);
    assertThat(principal.getJsonArray("scopes").getList()).containsExactly("data-access");
  }

  @Test
  @DisplayName("a caller with no organisation reaches the scope check without a membership lookup")
  void noOrganisationNotGranted() {
    givenPrincipal(new JsonObject());

    guard.withDeptScopes(scopeCheck).handle(ctx);

    verify(scopeCheck).handle(ctx);
    verifyNoInteractions(organizationUserDAO, hierarchyService);
  }

  @Test
  @DisplayName("a delegated principal is never granted the dept scopes")
  void delegateNotGranted() {
    givenPrincipal(
        new JsonObject()
            .put("organisation_id", orgId.toString())
            .put("delegatee_sub", UUID.randomUUID().toString()));

    guard.withDeptScopes(scopeCheck).handle(ctx);

    verify(scopeCheck).handle(ctx);
    verifyNoInteractions(organizationUserDAO, hierarchyService);
    assertThat(principal.getJsonArray("scopes").getList()).containsExactly("data-access");
  }

  @Test
  @DisplayName("a failed membership lookup fails the request instead of reaching the scope check")
  void lookupFailureFailsClosed() {
    givenPrincipal(new JsonObject().put("organisation_id", orgId.toString()));
    RuntimeException dbError = new RuntimeException("db down");
    when(organizationUserDAO.getByOrganizationIdAndUserId(orgId, userId))
        .thenReturn(Future.failedFuture(dbError));

    guard.withDeptScopes(scopeCheck).handle(ctx);

    verify(ctx).fail(dbError);
    verifyNoInteractions(scopeCheck);
  }

  @Test
  @DisplayName("strict coverage excludes the administered department itself")
  void strictCoverageExcludesOwnDepartment() {
    DeptAdminScope scope =
        new DeptAdminScope(orgId, userId, adminNode, List.of(adminNode, childNode));

    assertThat(scope.covers(adminNode)).isTrue();
    assertThat(scope.covers(childNode)).isTrue();
    assertThat(scope.covers(UUID.randomUUID())).isFalse();
    assertThat(scope.covers(null)).isFalse();
    assertThat(scope.coversStrictly(adminNode)).isFalse();
    assertThat(scope.coversStrictly(childNode)).isTrue();
    assertThat(scope.strictNodeIdStrings()).containsExactly(childNode.toString());
  }
}
