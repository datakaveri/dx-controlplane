package org.cdpg.dx.aaa.organization.handler;

import static org.cdpg.dx.aaa.organization.config.Constants.ALLOWED_FILTER_MAP_FOR_ORG_USERS;
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_ORG_USERS;
import static org.cdpg.dx.aaa.organization.config.Constants.CREATED_AT;
import static org.cdpg.dx.aaa.organization.config.Constants.ORGANIZATION_ID;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auth.authentication.util.AccessValidator;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
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
    // gets org_user info
    // delegate requirement: scope - org_management and delegator is org_admin or cos_admin
    // delegation table has the org_id

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.ORG_ADMIN.getRole()),
        List.of(DxScope.USER_MANAGEMENT.getScope(),DxScope.ORG_ADMIN_ACCESS.getScope()));

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    // TODO check this belogns to the org

    userService
        .getUserInfoByID(userId)
        .onSuccess(
            users -> {
              ActivityAuditLogBuilder audit =
                  OrganizationAuditHelper.buildViewOrganizationUserInfoAudit(ctx, orgId, userId);

              RoutingContextHelper.setAuditingLogNew(ctx, audit);

              ResponseBuilder.sendSuccess(ctx, users, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getOrganisationUsers(RoutingContext ctx) {
    // gets org_user
    // delegate requirement: scope - org_management and delegator is org_admin
    // delegation table has the org_id

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.ORG_ADMIN.getRole()),
        List.of(DxScope.USER_MANAGEMENT.getScope(),DxScope.ORG_ADMIN_ACCESS.getScope()));

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_USERS)
            .apiToDbMap(API_TO_DB_ORG_USERS)
            .additionalFilters(Map.of(ORGANIZATION_ID, orgId.toString()))
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
              ActivityAuditLogBuilder audit =
                  OrganizationAuditHelper.buildViewOrganizationUsersAudit(ctx, orgId);
              RoutingContextHelper.setAuditingLogNew(ctx, audit);

              ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void updateOrganisationUserRole(RoutingContext ctx) {

    // updates org_user role
    // delegate requirement: scope - org_management and delegator is org_admin or cos_admin
    // delegation table has org_id

    JsonObject OrgRequestJson = ctx.body().asJsonObject();

    Role role;
    role = Role.fromString(OrgRequestJson.getString("role"));

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    organizationService
        .updateUserRole(orgId, userId, role)
        .onSuccess(
            updated -> {
              if (updated) {
                ActivityAuditLogBuilder audit =
                    OrganizationAuditHelper.buildUpdateOrganizationUserRoleAudit(
                        ctx, orgId, userId, role.getRoleName());

                RoutingContextHelper.setAuditingLogNew(ctx, audit);
                ResponseBuilder.sendSuccess(ctx, "Updated Organisation User Role", urnGenerator);

              } else {
                ctx.fail(new DxNotFoundException("Organisation User Not Found"));
              }
            })
        .onFailure(ctx::fail);
    ;
  }

  public void deleteOrganisationUserById(RoutingContext ctx) {

    // deletes org_user
    // delegate requirement: scope - org_management and delegator is org_admin or cos_admin
    // delegation table has the org_id
    // get actual org admin id from the delegation table

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    if (orgId == null || userId == null) {
      ctx.fail(new DxNotFoundException("Organization ID or User ID is missing"));
      return;
    }

    UUID orgAdminId = UUID.fromString(ctx.user().subject());

    userService
        .getUserInfoByID(userId)
        .compose(
            user -> {
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

                              ActivityAuditLogBuilder audit =
                                  OrganizationAuditHelper.buildRemoveOrganizationUserAudit(
                                      ctx, orgId, userId);

                              RoutingContextHelper.setAuditingLogNew(ctx, audit);

                              ResponseBuilder.sendSuccess(
                                  ctx,
                                  "User deleted successfully from DB and Keycloak",
                                  urnGenerator);
                            })
                        .onFailure(ctx::fail);
                  });
            });
  }
}
