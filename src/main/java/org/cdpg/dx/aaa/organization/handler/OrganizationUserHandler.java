package org.cdpg.dx.aaa.organization.handler;

import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.organization.config.Constants.ALLOWED_FILTER_MAP_FOR_ORG_USERS;
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_ORG_USERS;
import static org.cdpg.dx.aaa.organization.config.Constants.CREATED_AT;
import static org.cdpg.dx.aaa.organization.config.Constants.HIERARCHY_NODE_ID;
import static org.cdpg.dx.aaa.organization.config.Constants.ORGANIZATION_ID;
import static org.cdpg.dx.aaa.organization.config.Constants.USER_NAME;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.keycloak.config.KeycloakConstants;

public class OrganizationUserHandler {
  private static final Logger LOGGER = LogManager.getLogger(OrganizationUserHandler.class);

  private final OrganizationService organizationService;
  private final UserService userService;
  private final URNGenerator urnGenerator;

  public OrganizationUserHandler(
      OrganizationService organizationService, UserService userService, URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.userService = userService;
    this.urnGenerator = urnGenerator;
  }

  public void getOrganisationUserInfo(RoutingContext ctx) {
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    if (!orgId.toString().equals(dxUser.organisationId())) {
      ctx.fail(
          new DxForbiddenException("The org id of the user and the path parameter are not same"));
      return;
    }
    requireMemberInDeptScope(DeptAdminAccessHandler.scopeOf(ctx), userId)
        .compose(member -> userService.getUserInfoByID(userId))
        .onSuccess(
            users -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, users.toJson(), OrganisationAuditOperation.GET_USER_INFO);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, users, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getOrganisationUsers(RoutingContext ctx) {
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    if (!orgId.toString().equals(dxUser.organisationId())) {
      ctx.fail(
          new DxForbiddenException("The org id of the user and the path parameter are not same"));
      return;
    }

    listOrganisationUsers(
        ctx, ALLOWED_FILTER_MAP_FOR_ORG_USERS, Map.of(ORGANIZATION_ID, orgId.toString()));
  }

  /**
   * Dept Admin: members of the department the caller administers and all of its
   * sub-departments, narrowable by {@code ?departmentScope=}. Only reachable by a dept_admin (the
   * route guard publishes their subtree).
   */
  public void getDepartmentUsers(RoutingContext ctx) {
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    DeptAdminScope deptAdminScope = DeptAdminAccessHandler.scopeOf(ctx);

    if (deptAdminScope == null || !deptAdminScope.organizationId().equals(orgId)) {
      ctx.fail(
          new DxForbiddenException("The org id of the user and the path parameter are not same"));
      return;
    }

    listOrganisationUsers(
        ctx,
        DeptAdminAccessHandler.withDepartmentScopeParam(
            ALLOWED_FILTER_MAP_FOR_ORG_USERS, HIERARCHY_NODE_ID),
        Map.of(
            ORGANIZATION_ID, orgId.toString(),
            HIERARCHY_NODE_ID, DeptAdminAccessHandler.requestedNodeIds(ctx, deptAdminScope)));
  }

  private void listOrganisationUsers(
      RoutingContext ctx,
      Map<String, String> allowedFilters,
      Map<String, Object> additionalFilters) {
    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(allowedFilters)
            .fuzzyFiltersDbMap(Map.of("userName", USER_NAME))
            .apiToDbMap(API_TO_DB_ORG_USERS)
            .additionalFilters(additionalFilters)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(API_TO_DB_ORG_USERS.keySet())
            .build();
    organizationService
        .getOrganizationUsers(request)
        .compose(
            res ->
                userService
                    .enrichWithUserRoles(
                        res.data(), OrganizationUser::userId, OrganizationUser::toJson)
                    .map(enriched -> Map.entry(enriched, res.paginationInfo())))
        .onSuccess(
            entry -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, new JsonObject(), OrganisationAuditOperation.GET_USERS);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void updateOrganisationUserRole(RoutingContext ctx) {

    JsonObject OrgRequestJson = ctx.body().asJsonObject();

    Role role;
    role = Role.fromString(OrgRequestJson.getString("role"));

    if (role == Role.DEPT_ADMIN) {
      ctx.fail(
          new DxBadRequestException(
              "Use the dept-admin assignment endpoint to make a user a department admin --"
                  + " it also records which department they administer"));
      return;
    }

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    if (!orgId.toString().equals(dxUser.organisationId())) {
      ctx.fail(
          new DxForbiddenException("The org id of the user and the path parameter are not same"));
      return;
    }

    DeptAdminScope deptAdminScope = DeptAdminAccessHandler.scopeOf(ctx);
    if (deptAdminScope != null && role != Role.USER) {
      // A dept_admin may only demote within their subtree, never grant org_admin.
      ctx.fail(new DxForbiddenException("A department admin can only set the member role"));
      return;
    }

    requireManageableByDeptAdmin(deptAdminScope, userId)
        .compose(v -> organizationService.updateUserRole(orgId, userId, role))
        .onSuccess(
            updated -> {
              if (updated) {
                UserActivityAuditLogBuilder auditLogBuilder =
                    OrganizationAuditHelper.buildOrganisationAudit(
                        ctx,
                        new JsonObject().put(ID, userId.toString()),
                        OrganisationAuditOperation.UPDATE_USER_INFO);
                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

                ResponseBuilder.sendSuccess(ctx, "Updated Organisation User Role", urnGenerator);

              } else {
                ctx.fail(new DxNotFoundException("Organisation User Not Found"));
              }
            })
        .onFailure(ctx::fail);
  }

  public void deleteOrganisationUserById(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    if (orgId == null || userId == null) {
      ctx.fail(new DxNotFoundException("Organization ID or User ID is missing"));
      return;
    }

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    if (!orgId.toString().equals(dxUser.organisationId())) {
      ctx.fail(
          new DxForbiddenException("The org id of the user and the path parameter are not same"));
      return;
    }

    // A removed provider's items are handed over to the org_admin -- the
    // caller themself when they are the org_admin.
    DeptAdminScope deptAdminScope = DeptAdminAccessHandler.scopeOf(ctx);

    requireManageableByDeptAdmin(deptAdminScope, userId)
        .compose(
            v ->
                deptAdminScope == null
                    ? Future.succeededFuture(dxUser.sub())
                    : organizationService.getUserOrgAdminId(orgId))
        .compose(
            orgAdminId ->
                userService.getUserInfoByID(userId).map(user -> Map.entry(user, orgAdminId)))
        .compose(
            userAndOrgAdmin -> {
              var user = userAndOrgAdmin.getKey();
              UUID orgAdminId = userAndOrgAdmin.getValue();
              if (user == null) {
                return Future.failedFuture(new DxNotFoundException("User not found"));
              }

              if (user.roles().contains(KeycloakConstants.ORG_ADMIN_ROLE)) {
                return Future.failedFuture(new DxBadRequestException("Cannot delete admin user"));
              }

              Future<Boolean> deletionFuture;
              if (user.roles().contains("provider")) {
                deletionFuture = organizationService.deleteProviderUser(userId, orgAdminId, orgId);
              } else {
                deletionFuture = organizationService.deleteOrganizationUser(orgId, userId);
              }

              return deletionFuture.compose(
                  deleted -> {
                    if (!deleted) {
                      return Future.failedFuture(
                          new DxNotFoundException("User not found in organization"));
                    }
                    return organizationService
                        .deleteOrganizationJoinRequest(orgId, userId)
                        .recover(
                            err -> {
                              LOGGER.warn("Failed to delete join request: {}", err.getMessage());
                              return Future.succeededFuture();
                            })
                        .compose(
                            v ->
                                organizationService
                                    .deleteProviderRoleRequest(orgId, userId)
                                    .recover(
                                        err -> {
                                          LOGGER.warn(
                                              "Failed to delete provider role request: {}",
                                              err.getMessage());
                                          return Future.succeededFuture();
                                        }))
                        .onSuccess(
                            v -> {
                              LOGGER.info(
                                  "User {} deleted completely from Organization {}", userId, orgId);

                              UserActivityAuditLogBuilder auditLogBuilder =
                                  OrganizationAuditHelper.buildOrganisationAudit(
                                      ctx,
                                      new JsonObject().put(ID, userId.toString()),
                                      OrganisationAuditOperation.DELETE_USER);
                              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

                              ResponseBuilder.sendSuccess(
                                  ctx,
                                  "User deleted successfully from DB and Keycloak",
                                  urnGenerator);
                            });
                  });
            })
        .onFailure(ctx::fail);
  }

  /**
   * For a dept_admin caller, the target's membership if they belong to a department in the
   * caller's subtree (fails otherwise). For any other caller, succeeds with null and checks
   * nothing -- the existing org-level behaviour.
   */
  private Future<OrganizationUser> requireMemberInDeptScope(
      DeptAdminScope deptAdminScope, UUID targetUserId) {
    if (deptAdminScope == null) {
      return Future.succeededFuture(null);
    }

    return organizationService
        .getOrganisationUserByUserId(targetUserId)
        .recover(err -> Future.failedFuture(new DxNotFoundException("Organisation User Not Found")))
        .compose(
            target -> {
              if (!target.organizationId().equals(deptAdminScope.organizationId())
                  || !deptAdminScope.covers(target.hierarchyNodeId())) {
                return Future.failedFuture(
                    new DxForbiddenException(
                        "User is not a member of a department you administer"));
              }
              return Future.succeededFuture(target);
            });
  }

  /**
   * Like {@link #requireMemberInDeptScope}, but for changes to the target's membership: a
   * dept_admin may not act on themself, the org_admin, or the admin of their own or a higher
   * department -- only on members and the admins of strictly lower departments.
   */
  private Future<Void> requireManageableByDeptAdmin(
      DeptAdminScope deptAdminScope, UUID targetUserId) {
    if (deptAdminScope == null) {
      return Future.succeededFuture();
    }

    if (deptAdminScope.userId().equals(targetUserId)) {
      return Future.failedFuture(
          new DxForbiddenException("A department admin cannot change their own membership"));
    }

    return requireMemberInDeptScope(deptAdminScope, targetUserId)
        .compose(
            target -> {
              if (target.role() == Role.ADMIN) {
                return Future.failedFuture(
                    new DxForbiddenException("Cannot change the organisation admin"));
              }
              if (target.role() == Role.DEPT_ADMIN
                  && !deptAdminScope.coversStrictly(target.adminOfNodeId())) {
                return Future.failedFuture(
                    new DxForbiddenException(
                        "Cannot change the admin of your own or a higher department"));
              }
              return Future.succeededFuture();
            });
  }
}
