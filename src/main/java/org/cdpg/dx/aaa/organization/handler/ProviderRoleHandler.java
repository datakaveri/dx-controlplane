package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.ProviderRoleRequest;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_PROVIDER_ROLE_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.CREATED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;
import static org.cdpg.dx.keycloak.config.KeycloakConstants.ORGANISATION_ID;

public class ProviderRoleHandler {
  private static final Logger LOGGER = LogManager.getLogger(ProviderRoleHandler.class);

  private final OrganizationService organizationService;
  private final UserService userService;
  private final EmailComposer emailComposer;
  private final URNGenerator urnGenerator;

  public ProviderRoleHandler(
      OrganizationService organizationService,
      UserService userService,
      EmailComposer emailComposer,
      URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.userService = userService;
    this.emailComposer = emailComposer;
    this.urnGenerator = urnGenerator;
  }

  public void createProviderRequest(RoutingContext ctx) {

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    User user = ctx.user();

    String userId = dxUser.sub().toString();
    String orgID = dxUser.organisationId();

    if (userId == null || userId.isEmpty()) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    if (orgID == null || orgID.isEmpty()) {
      ctx.fail(new DxForbiddenException("User is not part any organisation"));
      return;
    }

    JsonObject req = new JsonObject().put("user_id", userId).put("organization_id", orgID);

    ProviderRoleRequest providerRoleRequest = ProviderRoleRequest.fromJson(req);

    organizationService
      .createProviderRequest(providerRoleRequest)
      .onSuccess(
        requests -> {
          UserActivityAuditLogBuilder auditLogBuilder =
            OrganizationAuditHelper.buildOrganisationAudit(
              ctx, requests.toJson(), OrganisationAuditOperation.REQUEST_PROVIDER_ROLE);
          CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

          ResponseBuilder.sendSuccess(ctx, "Created Request", urnGenerator);
          Future<Void> future =
            emailComposer.sendEmailForProviderRole(providerRoleRequest, user);
        })
      .onFailure(ctx::fail);
  }


  public void updateProviderRequest(RoutingContext ctx) {

    JsonObject orgRequestJson = ctx.body().asJsonObject();
    UUID reqId = RequestHelper.getPathParamAsUUID(ctx, "id");
    Status status = Status.fromString(orgRequestJson.getString("status"));

    organizationService
      .updateProviderRequestStatus(reqId, status)
      .onSuccess(
        v -> {

          UserActivityAuditLogBuilder auditLogBuilder =
            OrganizationAuditHelper.buildOrganisationAudit(
              ctx, new JsonObject().put(ID, reqId.toString()), OrganisationAuditOperation.UPDATE_PROVIDER_REQUEST);
          CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

          emailComposer
            .sendUserEmailForProviderRoleApproval(reqId, status)
            .onFailure(
              err -> LOGGER.error("Email send failed", err));

          ResponseBuilder.sendSuccess(
            ctx,
            "Provider role updated",
            urnGenerator);
        })
      .onFailure(ctx::fail);
  }


  public void getProviderRequest(RoutingContext ctx) {

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    String orgIdStr = dxUser.organisationId();
    if (orgIdStr == null || orgIdStr.isBlank()) {
      ctx.fail(new DxBadRequestException("User is not part of any organisation"));
      return;
    }
    UUID resOrgId = UUID.fromString(orgIdStr);

    PaginatedRequest request =
      PaginationRequestBuilder.from(ctx)
        .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_PROVIDER_ROLE_REQUEST)
        .apiToDbMap(API_TO_DB_PROVIDER_ROLE_REQUEST)
        .additionalFilters(Map.of(ORGANIZATION_ID, resOrgId.toString()))
        .allowedTimeFields(Set.of(CREATED_AT))
        .defaultTimeField(CREATED_AT)
        .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
        .allowedSortFields(API_TO_DB_PROVIDER_ROLE_REQUEST.keySet())
        .build();

    organizationService.getAllPendingProviderRoleRequests(request)
      .compose(providerResult ->
        organizationService.getOrganizationJoinRequestsByOrgId(resOrgId)
          .map(joinRequests -> Map.entry(providerResult, joinRequests)))
      .compose(entry -> {

        PaginatedResult<ProviderRoleRequest> providerResult = entry.getKey();
        List<OrganizationJoinRequest> joinRequests = entry.getValue();

        Map<UUID, OrganizationJoinRequest> joinRequestMap =
          joinRequests.stream()
            .collect(Collectors.toMap(
              OrganizationJoinRequest::userId,
              Function.identity()
            ));

        return userService.enrichWithUserRoles(
          providerResult.data(),
          ProviderRoleRequest::userId,
          ProviderRoleRequest::toJson
        ).map(enriched -> {

          List<JsonObject> finalResponse = enriched.stream()
            .map(json -> {
              LOGGER.info("The json is : {}", json);
              UUID uid = UUID.fromString(json.getString("user_id"));
              OrganizationJoinRequest joinReq = joinRequestMap.get(uid);

              if (joinReq != null) {
                json.put("userName", joinReq.userName());
                json.put("jobTitle", joinReq.jobTitle());
                json.put("empId", joinReq.empId());
              }

              return json;
            })
            .toList();

          return Map.entry(finalResponse, providerResult.paginationInfo());
        });
      })
      .onSuccess(entry -> {

        UserActivityAuditLogBuilder auditLogBuilder =
          OrganizationAuditHelper.buildOrganisationAudit(
            ctx, new JsonObject(), OrganisationAuditOperation.GET_PROVIDER_REQS);
        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(
          ctx,
          entry.getKey(),
          entry.getValue(),
          urnGenerator
        );
      })
      .onFailure(ctx::fail);
  }

  public void deleteUserProviderRoleRequest(RoutingContext ctx) {

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();

    organizationService
        .getProviderRoleRequestByUserId(userId)
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(
                    new DxNotFoundException(
                        "No provider role request found for userId: " + userId));
              }

              if (!Status.PENDING.getStatus().equalsIgnoreCase(request.status())) {
                return Future.failedFuture(
                    new DxBadRequestException(
                        "Only pending provider role requests can be deleted"));
              }
              return organizationService
                  .deleteProviderRoleRequestById(request.id())
                  .compose(
                      deleted -> {
                        if (!deleted) {
                          return Future.failedFuture(
                              new DxNotFoundException("Failed to delete provider role request"));
                        }
                        return Future.succeededFuture(request);
                      });
            })
        .onSuccess(
            request -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                  ctx, new JsonObject().put(ID, request.id().toString()), OrganisationAuditOperation.DELETE_PENDING_PROVIDER_REQUEST);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(
                  ctx, "Provider Role Request deleted successfully", urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to delete provider role request for user {}: {}",
                  userId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  public void getProviderRoleRequest(RoutingContext ctx) {
    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();

    organizationService
        .getProviderRoleRequestByUserId(userId)
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(
                    new DxNotFoundException(
                        "No provider role request found for userId: " + userId));
              }

              return userService
                  .enrichWithUserRoles(
                      List.of(request), ProviderRoleRequest::userId, ProviderRoleRequest::toJson)
                  .map(list -> list.isEmpty() ? null : list.get(0));
            })
        .onSuccess(
            enriched -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                  ctx, enriched, OrganisationAuditOperation.GET_PROVIDER_REQS);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              if (enriched == null) {
                ResponseBuilder.sendSuccess(ctx, new JsonObject(), this.urnGenerator);
              } else {
                ResponseBuilder.sendSuccess(ctx, enriched, this.urnGenerator);
              }
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to fetch provider role request for user {}: {}",
                  userId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  public void createProviderRole(RoutingContext ctx) {

    JsonObject providerRequestJson = ctx.body().asJsonObject();
    ProviderRoleRequest providerRoleRequest = ProviderRoleRequest.fromJson(providerRequestJson);
    organizationService
      .createProviderRole(providerRoleRequest)
      .onSuccess(
        org -> {
          UserActivityAuditLogBuilder auditLogBuilder =
            OrganizationAuditHelper.buildOrganisationAudit(
              ctx, new JsonObject(), OrganisationAuditOperation.REQUEST_PROVIDER_ROLE);
          CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

          ResponseBuilder.sendSuccess(ctx, "Provider role granted successfully", urnGenerator);
        })
      .onFailure(
        err -> {
          if (err instanceof DxForbiddenException) {
            ctx.fail(
              new DxForbiddenException(
                "User is not part of any organisation or does not have permission to grant provider role"));
          } else if (err instanceof DxNotFoundException) {
            ctx.fail(
              new DxNotFoundException(
                "User not found or does not have a pending provider role request"));
          } else {
            ctx.fail(err);
          }
        });
  }
}