package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auth.authentication.util.AccessValidator;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.util.RoutingContextHelper;

import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_ORG_JOIN_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.REQUESTED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class OrganizationJoinRequestHandler {
  private static final Logger LOGGER = LogManager.getLogger(OrganizationJoinRequestHandler.class);

  private final OrganizationService organizationService;
  private final UserService userService;
  private final EmailComposer emailComposer;
  private final URNGenerator urnGenerator;

  public OrganizationJoinRequestHandler(
      OrganizationService organizationService,
      UserService userService,
      EmailComposer emailComposer,
      URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.userService = userService;
    this.emailComposer = emailComposer;
    this.urnGenerator = urnGenerator;
  }

  public void joinOrganisationRequest(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    JsonObject OrgRequestJson = ctx.body().asJsonObject();
    OrganizationJoinRequest organizationJoinRequest;
    User user = ctx.user();
    OrgRequestJson.put("user_id", user.subject());

    String userName = user.principal().getString("name");

    OrgRequestJson.put("user_name", userName);
    OrgRequestJson.put("organization_id", orgId.toString());

    System.out.println("OrgRequestJson: " + OrgRequestJson.encodePrettily());

    organizationJoinRequest = OrganizationJoinRequest.fromJson(OrgRequestJson);
    JsonObject jsonBody = organizationJoinRequest.toJson();
    String officialEmailStr = jsonBody.getString("official_email");

    organizationService
        .getAllOrganizationJoinRequests()
        .compose(
            joinRequests -> {
              for (OrganizationJoinRequest request : joinRequests) {

                //          if (request.organizationId().equals(orgId.toString())
                //            && request.userId().equals(user.subject()))
                if (request.organizationId().equals(orgId)
                    && request.userId().equals(UUID.fromString(user.subject()))) {

                  return Future.failedFuture(
                      new DxConflictException(
                          "User already has a pending/ granted join request for this organization"));
                }

                if (!request.status().equals(Status.REJECTED)
                    && request.officialEmail().equals(officialEmailStr)) {
                  return Future.failedFuture(
                      new DxConflictException("This email has been used already!"));
                }
              }

              return organizationService
                  .joinOrganizationRequest(organizationJoinRequest)
                  .onSuccess(
                      createdRequest -> {
                        ActivityAuditLogBuilder auditLog =
                            OrganizationAuditHelper.buildJoinOrgRequestAudit(
                                ctx, createdRequest.id(), createdRequest.organizationId(), null);
                        RoutingContextHelper.setAuditingLogNew(ctx, auditLog);

                        ResponseBuilder.sendSuccess(ctx, "Created Join request", urnGenerator);
                        Future<Void> future =
                            emailComposer.sendEmailForJoiningOrg(organizationJoinRequest, user);
                      })
                  .onFailure(ctx::fail);
            })
        .onFailure(ctx::fail);
  }

  public void approveJoinOrganisationRequests(RoutingContext ctx) {

    User user = ctx.user();
    JsonObject userJson = user.principal();
// deleagatoin requirement: scope - org_management and delegator is org_admin
    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.ORG_ADMIN.getRole()),
        List.of(DxScope.ORG_MANAGEMENT.getScope()));

    JsonObject OrgRequestJson = ctx.body().asJsonObject();

    UUID requestId = RequestHelper.getPathParamAsUUID(ctx, "req_id");

    Status status = Status.fromString(OrgRequestJson.getString("status"));

    organizationService
        .updateOrganizationJoinRequestStatus(requestId, status)
        .onSuccess(
            approved -> {
              if (approved) {

                AuditLog auditLog =
                    AuditingHelper.createAuditLog(
                        ctx.user(),
                        RoutingContextHelper.getRequestPath(ctx),
                        "PUT",
                        "Approved Join Request");
                RoutingContextHelper.setAuditingLog(ctx, auditLog);
                ResponseBuilder.sendSuccess(ctx, "Updated Organisation Join Request", urnGenerator);
                Future<Void> future =
                    emailComposer.sendUserEmailForOrgJoinRequestApproval(requestId, status);

              } else {
                ctx.fail(new DxNotFoundException("Request Not Found"));
              }
            })
        .onFailure(ctx::fail);
  }

  public void deleteUserJoinOrganisationRequests(RoutingContext ctx) {
    UUID requestId = UUID.fromString(ctx.pathParam("id"));
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    organizationService
        .getOrganizationJoinRequestById(requestId)
        .compose(
            request -> {
              if (request == null) {
                ctx.fail(new DxBadRequestException("Join organisation request not found"));
                return Future.failedFuture(
                    new DxBadRequestException("Join organisation request not found"));
              }

              if (!request.status().equals(Status.PENDING.getStatus())) {
                ctx.fail(new DxBadRequestException("Only pending requests can be deleted"));
                return Future.failedFuture(
                    new DxBadRequestException("Only pending requests can be deleted"));
              }

              if (!request.userId().equals(userId)) {
                ctx.fail(new DxForbiddenException("User is not authorized to delete this request"));
                return Future.failedFuture(
                    new DxForbiddenException("User is not authorized to delete this request"));
              }

              return organizationService
                  .deleteOrganizationJoinRequestById(requestId)
                  .compose(
                      deleted -> {
                        if (!deleted) {
                          return Future.failedFuture(
                              new DxNotFoundException(
                                  "Failed to delete join organisation request with ID: "
                                      + requestId));
                        }
                        AuditLog auditLog =
                            AuditingHelper.createAuditLog(
                                ctx.user(),
                                RoutingContextHelper.getRequestPath(ctx),
                                "DELETE",
                                "Deleted Join Organisation Request");
                        RoutingContextHelper.setAuditingLog(ctx, auditLog);
                        ResponseBuilder.sendSuccess(
                            ctx, "Join organisation request deleted successfully", urnGenerator);
                        return Future.succeededFuture(true);
                      });
            })
        .onFailure(ctx::fail);
  }

  public void getUserJoinOrganisationRequests(RoutingContext ctx) {
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    AuditLog auditLog =
        AuditingHelper.createAuditLog(
            ctx.user(),
            RoutingContextHelper.getRequestPath(ctx),
            "GET",
            "Get User Join Organisation Requests");

    organizationService
        .getOrganizationJoinRequestsByUser(userId)
        .compose(
            requests -> {
              List<JsonObject> result =
                  requests.stream()
                      .map(OrganizationJoinRequest::toJson)
                      .collect(Collectors.toList());
              return Future.succeededFuture(result);
            })
        .onSuccess(
            result -> {
              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to fetch join organisation requests for user {}: {}",
                  userId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  public void getJoinOrganisationRequests(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.ORG_ADMIN.getRole()),
        List.of(DxScope.ORG_MANAGEMENT.getScope()));

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_JOIN_REQUEST)
            .apiToDbMap(API_TO_DB_ORG_JOIN_REQUEST)
            .additionalFilters(Map.of(ORGANIZATION_ID, orgId.toString()))
            .allowedTimeFields(Set.of(REQUESTED_AT))
            .defaultTimeField(REQUESTED_AT)
            .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(API_TO_DB_ORG_JOIN_REQUEST.keySet())
            .build();

    organizationService
        .getOrganizationPendingJoinRequests(request)
        .compose(
            result ->
                userService
                    .enrichWithUserRoles(
                        result.data(),
                        OrganizationJoinRequest::userId,
                        OrganizationJoinRequest::toJson)
                    .map(enrichedList -> Map.entry(enrichedList, result.paginationInfo())))
        .onSuccess(
            entry -> {

              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "GET",
                      "Get Pending Join Requests");
              RoutingContextHelper.setAuditingLog(ctx, auditLog);

              ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }
}
