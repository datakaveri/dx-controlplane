package org.cdpg.dx.aaa.organization.handler;

import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.organization.config.Constants.ALLOWED_FILTER_MAP_FOR_ORG_USERS;
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_ORG_USERS;
import static org.cdpg.dx.aaa.organization.config.Constants.CREATED_AT;
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
      ctx.fail(new DxForbiddenException(
        "The org id of the user and the path parameter are not same"));
      return;
    }

    userService
        .getUserInfoByID(userId)
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
      ctx.fail(new DxForbiddenException(
        "The org id of the user and the path parameter are not same"));
      return;
    }

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_USERS)
            .fuzzyFiltersDbMap(Map.of("userName", USER_NAME))
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

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    if (!orgId.toString().equals(dxUser.organisationId())) {
      ctx.fail(new DxForbiddenException(
        "The org id of the user and the path parameter are not same"));
      return;
    }

    organizationService
        .updateUserRole(orgId, userId, role)
        .onSuccess(
            updated -> {
              if (updated) {
                UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                    ctx, new JsonObject().put(ID,userId.toString()), OrganisationAuditOperation.UPDATE_USER_INFO);
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
      ctx.fail(new DxForbiddenException(
        "The org id of the user and the path parameter are not same"));
      return;
    }

    UUID orgAdminId = dxUser.sub();

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

                              UserActivityAuditLogBuilder auditLogBuilder =
                                OrganizationAuditHelper.buildOrganisationAudit(
                                  ctx, new JsonObject().put(ID,userId.toString()) , OrganisationAuditOperation.DELETE_USER);
                              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

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