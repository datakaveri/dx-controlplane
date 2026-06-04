package org.cdpg.dx.aaa.provider.handler;

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
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_PLATFORM_PROVIDER_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.ALLOWED_FILTER_MAP_FOR_PLATFORM_PROVIDER_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.ALLOWED_SORT_FIELDS_PROVIDER_ROLE_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.CREATED_AT;
import static org.cdpg.dx.aaa.organization.config.Constants.PROVIDER_TYPE;
import static org.cdpg.dx.aaa.organization.config.Constants.PROVIDER_TYPE_PLATFORM;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

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

  /**
   * Consumer: submit a provider role request within their organisation. Requires the user to be an
   * org member (organisationId present in token).
   */
  public void createOrgProviderRequest(RoutingContext ctx) {

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    User user = ctx.user();

    String userId = dxUser.sub().toString();
    String orgID = dxUser.organisationId();

    if (userId == null || userId.isEmpty()) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    if (orgID == null || orgID.isEmpty()) {
      ctx.fail(new DxForbiddenException("User is not part of any organisation"));
      return;
    }

    JsonObject req =
        new JsonObject()
            .put("user_id", userId)
            .put("organization_id", orgID)
            .put("provider_type", PROVIDER_TYPE_ORG);

    ProviderRoleRequest providerRoleRequest = ProviderRoleRequest.fromJson(req);

    organizationService
        .createProviderRequest(providerRoleRequest)
        .onSuccess(
            requests -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, requests.toJson(), OrganisationAuditOperation.REQUEST_PROVIDER_ROLE);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, "Provider role request submitted successfully", urnGenerator);
              emailComposer
                  .sendEmailForProviderRole(providerRoleRequest, user)
                  .onFailure(err -> LOGGER.error("Provider role email failed", err));
            })
        .onFailure(ctx::fail);
  }

  /** Consumer: submit a platform-level provider role request (no org membership required). */
  public void createPlatformProviderRequest(RoutingContext ctx) {

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    User user = ctx.user();
    String userId = dxUser.sub().toString();

    if (userId == null || userId.isEmpty()) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    JsonObject req =
        new JsonObject().put("user_id", userId).put("provider_type", PROVIDER_TYPE_PLATFORM);

    ProviderRoleRequest providerRoleRequest = ProviderRoleRequest.fromJson(req);

    organizationService
        .createProviderRequest(providerRoleRequest)
        .onSuccess(
            requests -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx,
                      requests.toJson(),
                      OrganisationAuditOperation.REQUEST_PLATFORM_PROVIDER_ROLE);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, "Provider role request submitted successfully", urnGenerator);
              emailComposer
                  .sendEmailForPlatformProviderRole(providerRoleRequest, user)
                  .onFailure(err -> LOGGER.error("Platform provider role email failed", err));
            })
        .onFailure(ctx::fail);
  }

  /** Org Admin: approve or reject a pending org provider role request by request ID. */
  public void updateOrgProviderRequest(RoutingContext ctx) {

    JsonObject orgRequestJson = ctx.body().asJsonObject();
    UUID reqId = RequestHelper.getPathParamAsUUID(ctx, "id");
    Status status = Status.fromString(orgRequestJson.getString("status"));

    organizationService
        .updateProviderRequestStatus(reqId, status)
        .onSuccess(
            v -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx,
                      new JsonObject().put(ID, reqId.toString()),
                      OrganisationAuditOperation.UPDATE_PROVIDER_REQUEST);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              emailComposer
                  .sendUserEmailForProviderRoleApproval(reqId, status)
                  .onFailure(err -> LOGGER.error("Email send failed", err));

              String message = status == Status.GRANTED
                  ? "Provider role request approved successfully"
                  : "Provider role request rejected successfully";
              ResponseBuilder.sendSuccess(ctx, message, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  /**
   * Org Admin: list all provider role requests within their organisation (paginated), enriched with
   * user roles and join request details (userName, jobTitle, empId).
   */
  public void getOrgProviderRequest(RoutingContext ctx) {

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

    organizationService
        .getAllPendingProviderRoleRequests(request)
        .compose(
            providerResult ->
                organizationService
                    .getOrganizationJoinRequestsByOrgId(resOrgId)
                    .map(joinRequests -> Map.entry(providerResult, joinRequests)))
        .compose(
            entry -> {
              PaginatedResult<ProviderRoleRequest> providerResult = entry.getKey();
              List<OrganizationJoinRequest> joinRequests = entry.getValue();

              Map<UUID, OrganizationJoinRequest> joinRequestMap =
                  joinRequests.stream()
                      .collect(
                          Collectors.toMap(OrganizationJoinRequest::userId, Function.identity()));

              return userService
                  .enrichWithUserRoles(
                      providerResult.data(),
                      ProviderRoleRequest::userId,
                      ProviderRoleRequest::toJson)
                  .map(
                      enriched -> {
                        List<JsonObject> finalResponse =
                            enriched.stream()
                                .map(
                                    json -> {
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
        .onSuccess(
            entry -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, new JsonObject(), OrganisationAuditOperation.GET_PROVIDER_REQS);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  /**
   * Consumer: withdraw their own pending org provider role request. Only PENDING requests can be
   * deleted. Requires org membership.
   */
  public void deleteOrgUserProviderRoleRequest(RoutingContext ctx) {

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();

    if (dxUser.organisationId() == null || dxUser.organisationId().isBlank()) {
      ctx.fail(new DxForbiddenException("User is not part of any organisation"));
      return;
    }

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
                      ctx,
                      new JsonObject().put(ID, request.id().toString()),
                      OrganisationAuditOperation.DELETE_PENDING_PROVIDER_REQUEST);
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

  /**
   * Consumer: get their own pending org provider role request, enriched with user roles. Requires
   * org membership.
   */
  public void getOrgProviderRoleRequest(RoutingContext ctx) {
    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();

    if (dxUser.organisationId() == null || dxUser.organisationId().isBlank()) {
      ctx.fail(new DxForbiddenException("User is not part of any organisation"));
      return;
    }

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

  /**
   * Org Admin: directly grant provider role to a user within their organisation, either by creating
   * a new granted request or approving an existing pending one.
   */
  public void createProviderRoleWithinOrg(RoutingContext ctx) {

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

  /** Consumer: get their own pending platform provider role request, enriched with user roles. */
  public void getPlatformUProviderRequest(RoutingContext ctx) {
    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();

    organizationService
        .getPlatformProviderRoleRequestByUserId(userId)
        .compose(
            request ->
                userService
                    .enrichWithUserRoles(
                        List.of(request), ProviderRoleRequest::userId, ProviderRoleRequest::toJson)
                    .map(list -> list.isEmpty() ? null : list.get(0)))
        .onSuccess(
            enriched -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, enriched, OrganisationAuditOperation.GET_PLATFORM_PROVIDER_REQS);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, enriched, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  /**
   * Consumer: withdraw their own pending platform provider role request. Only PENDING requests can
   * be deleted.
   */
  public void deletePlatformUserProviderRequest(RoutingContext ctx) {
    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();

    organizationService
        .getPlatformProviderRoleRequestByUserId(userId)
        .compose(
            request -> {
              if (!Status.PENDING.getStatus().equalsIgnoreCase(request.status())) {
                return Future.failedFuture(
                    new DxBadRequestException(
                        "Only pending platform provider role requests can be deleted"));
              }
              return organizationService
                  .deleteProviderRoleRequestById(request.id())
                  .map(deleted -> request);
            })
        .onSuccess(
            request -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx,
                      new JsonObject().put(ID, request.id().toString()),
                      OrganisationAuditOperation.DELETE_PENDING_PROVIDER_REQUEST);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(
                  ctx, "Platform provider role request deleted successfully", urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to delete platform provider role request for user {}: {}",
                  userId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  /** COS Admin: list all platform provider role requests across all users (paginated), enriched with user roles. */
  public void getPlatformProviderRequestsForAdmin(RoutingContext ctx) {
    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_PLATFORM_PROVIDER_REQUEST)
            .apiToDbMap(API_TO_DB_PLATFORM_PROVIDER_REQUEST)
            .additionalFilters(Map.of(PROVIDER_TYPE, PROVIDER_TYPE_PLATFORM))
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(ALLOWED_SORT_FIELDS_PROVIDER_ROLE_REQUEST)
            .build();

    organizationService
        .getAllPlatformProviderRequests(request)
        .compose(
            result ->
                userService
                    .enrichWithUserRoles(
                        result.data(), ProviderRoleRequest::userId, ProviderRoleRequest::toJson)
                    .map(enriched -> Map.entry(enriched, result.paginationInfo())))
        .onSuccess(
            entry -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, new JsonObject(), OrganisationAuditOperation.GET_PLATFORM_PROVIDER_REQS);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  /** COS Admin: approve or reject a pending platform provider role request by request ID. */
  public void updatePlatformProviderRequestForAdmin(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    UUID reqId = RequestHelper.getPathParamAsUUID(ctx, "id");
    Status status = Status.fromString(body.getString("status"));

    organizationService
        .updateProviderRequestStatus(reqId, status)
        .onSuccess(
            v -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx,
                      new JsonObject().put(ID, reqId.toString()),
                      OrganisationAuditOperation.UPDATE_PLATFORM_PROVIDER_REQUEST);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              emailComposer
                  .sendUserEmailForProviderRoleApproval(reqId, status)
                  .onFailure(err -> LOGGER.error("Email send failed", err));

              String message = status == Status.GRANTED
                  ? "Provider role request approved successfully"
                  : "Provider role request rejected successfully";
              ResponseBuilder.sendSuccess(ctx, message, urnGenerator);
            })
        .onFailure(ctx::fail);
  }
}
