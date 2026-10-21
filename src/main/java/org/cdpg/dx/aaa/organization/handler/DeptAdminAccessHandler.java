package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.dao.impl.OrganizationDAOImpl;
import org.cdpg.dx.aaa.organization.dao.impl.OrganizationHierarchyNodeDAOImpl;
import org.cdpg.dx.aaa.organization.dao.impl.OrganizationUserDAOImpl;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope.DepartmentFilter;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.aaa.organization.service.impl.OrganizationHierarchyServiceImpl;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.config.KeycloakConstants;

/**
 * Grants the department-level scopes ({@link Scopes#DEPT_ADMIN}) to a dept_admin caller ahead of
 * the route's {@code AuthorizationHandler} scope check.
 *
 * <p>dept_admin is an organization-membership role ({@code organization_users.role} +
 * {@code admin_of_node_id}), not a Keycloak realm role, so a dept_admin's token carries no
 * department scopes. This handler resolves the caller's membership from the database instead:
 *
 * <ul>
 *   <li>caller is a dept_admin of their token's organization -- the dept scopes are added to
 *       their principal, and their department subtree is published as a {@link DeptAdminScope}
 *       (see {@link #scopeOf}) that handlers must narrow every query/action to;
 *   <li>anyone else -- passes through unchanged, so only their token's own scopes count.
 * </ul>
 *
 * <p>Delegated and app-credential principals never resolve as dept_admin: a delegate must not
 * inherit the delegator's department authority through this path.
 */
public class DeptAdminAccessHandler {
  private static final Logger LOGGER = LogManager.getLogger(DeptAdminAccessHandler.class);

  public static final String DEPT_ADMIN_SCOPE_KEY = "deptAdminScope";
  public static final String DEPARTMENT_SCOPE_PARAM = "departmentScope";

  private final OrganizationUserDAO organizationUserDAO;
  private final OrganizationHierarchyService hierarchyService;

  public DeptAdminAccessHandler(
      OrganizationUserDAO organizationUserDAO, OrganizationHierarchyService hierarchyService) {
    this.organizationUserDAO = organizationUserDAO;
    this.hierarchyService = hierarchyService;
  }

  /**
   * Narrow, DAO-level dependencies only -- same rationale as ItemControllerFactory: avoids a
   * circular dependency on the full OrganizationService.
   */
  public static DeptAdminAccessHandler create(PostgresService pgService) {
    return new DeptAdminAccessHandler(
        new OrganizationUserDAOImpl(pgService),
        new OrganizationHierarchyServiceImpl(
            new OrganizationDAOImpl(pgService), new OrganizationHierarchyNodeDAOImpl(pgService)));
  }

  /** The caller's dept_admin subtree, or null when the caller is not a dept_admin. */
  public static DeptAdminScope scopeOf(RoutingContext ctx) {
    return ctx.get(DEPT_ADMIN_SCOPE_KEY);
  }

  /**
   * The department ids a dept_admin list endpoint should return, narrowed by the optional
   * {@code ?departmentScope=all|own|sub} query param (default {@code all}).
   */
  public static List<String> requestedNodeIds(RoutingContext ctx, DeptAdminScope scope) {
    try {
      return scope.nodeIdStrings(
          DepartmentFilter.fromParam(ctx.queryParams().get(DEPARTMENT_SCOPE_PARAM)));
    } catch (IllegalArgumentException e) {
      throw new DxBadRequestException(e.getMessage());
    }
  }

  /**
   * {@code allowedFilters} plus {@link #DEPARTMENT_SCOPE_PARAM}, so that PaginationRequestBuilder's
   * unknown-param check accepts it. The param is mapped onto {@code nodeColumn} -- the same column
   * the handler's department additionalFilters set -- so its raw value is always overridden by
   * {@link #requestedNodeIds} and never reaches the query itself.
   */
  public static Map<String, String> withDepartmentScopeParam(
      Map<String, String> allowedFilters, String nodeColumn) {
    Map<String, String> filters = new HashMap<>(allowedFilters);
    filters.put(DEPARTMENT_SCOPE_PARAM, nodeColumn);
    return filters;
  }

  /**
   * Grants the dept scopes to a dept_admin caller (publishing their subtree), then runs {@code
   * scopeCheck} -- the route's {@code AuthorizationHandler} guard -- for every caller.
   */
  public Handler<RoutingContext> withDeptScopes(Handler<RoutingContext> scopeCheck) {
    return ctx ->
        resolve(ctx)
            .onSuccess(
                scope -> {
                  if (scope != null) {
                    grantDeptScopes(ctx);
                    ctx.put(DEPT_ADMIN_SCOPE_KEY, scope);
                  }
                  scopeCheck.handle(ctx);
                })
            .onFailure(ctx::fail);
  }

  private static void grantDeptScopes(RoutingContext ctx) {
    JsonObject principal = ctx.user().principal();
    JsonArray scopes =
        principal.getJsonArray(KeycloakConstants.CLAIM_SCOPES, new JsonArray()).copy();
    Scopes.DEPT_ADMIN.stream().filter(s -> !scopes.contains(s)).forEach(scopes::add);
    principal.put(KeycloakConstants.CLAIM_SCOPES, scopes);
  }

  private Future<DeptAdminScope> resolve(RoutingContext ctx) {
    if (ctx.user() == null) {
      // Let the scope check produce its usual 401.
      return Future.succeededFuture(null);
    }

    DxUser dxUser;
    try {
      dxUser = RoutingContextHelper.fromPrincipal(ctx);
    } catch (RuntimeException e) {
      return Future.succeededFuture(null);
    }

    if (dxUser.delegateeId() != null || dxUser.appId() != null) {
      return Future.succeededFuture(null);
    }

    String orgIdStr = dxUser.organisationId();
    if (orgIdStr == null || orgIdStr.isBlank()) {
      return Future.succeededFuture(null);
    }

    UUID orgId;
    try {
      orgId = UUID.fromString(orgIdStr);
    } catch (IllegalArgumentException e) {
      return Future.succeededFuture(null);
    }
    UUID userId = dxUser.sub();

    return organizationUserDAO
        .getByOrganizationIdAndUserId(orgId, userId)
        .compose(
            orgUser -> {
              if (orgUser == null
                  || orgUser.role() != Role.DEPT_ADMIN
                  || orgUser.adminOfNodeId() == null) {
                return Future.succeededFuture(null);
              }

              return hierarchyService
                  .getDescendantNodeIds(orgId, orgUser.adminOfNodeId())
                  .map(
                      (List<UUID> nodeIds) -> {
                        if (nodeIds == null || nodeIds.isEmpty()) {
                          return null;
                        }
                        LOGGER.debug(
                            "Caller {} is dept_admin of {} in org {} ({} departments in scope)",
                            userId,
                            orgUser.adminOfNodeId(),
                            orgId,
                            nodeIds.size());
                        return new DeptAdminScope(
                            orgId, userId, orgUser.adminOfNodeId(), List.copyOf(nodeIds));
                      });
            });
  }
}
