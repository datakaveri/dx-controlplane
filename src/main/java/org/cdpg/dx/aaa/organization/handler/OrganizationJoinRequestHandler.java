package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.OrgOwnershipValidator;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auth.authentication.util.AccessValidator;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
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
import static org.cdpg.dx.keycloak.config.KeycloakConstants.ORGANISATION_NAME;

public class OrganizationJoinRequestHandler {
  private static final Logger LOGGER = LogManager.getLogger(OrganizationJoinRequestHandler.class);

  private final OrganizationService organizationService;
  private final UserService userService;
  private final EmailComposer emailComposer;
  private final URNGenerator urnGenerator;
  private final OrgOwnershipValidator orgOwnershipValidator;

  public OrganizationJoinRequestHandler(
      OrganizationService organizationService,
      OrgOwnershipValidator orgOwnershipValidator,
      UserService userService,
      EmailComposer emailComposer,
      URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.orgOwnershipValidator = orgOwnershipValidator;
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
                                ctx,
                                createdRequest.id(),
                                createdRequest.organizationId(),
                                "member");
                        RoutingContextHelper.setAuditingLogNew(ctx, auditLog);

                        ResponseBuilder.sendSuccess(ctx, "Created Join request", urnGenerator);
                        Future<Void> future =
                            emailComposer.sendEmailForJoiningOrg(organizationJoinRequest, user);
                      })
                  .onFailure(ctx::fail);
            })
        .onFailure(ctx::fail);
  }

  public void getJoinOrganisationRequests(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    User user = ctx.user();
    JsonObject userJson = user.principal();

    String delegatorStr = ctx.queryParams().get("delegatorId");
    String orgIdparam = ctx.pathParam("id");
    UUID delegatorId = delegatorStr != null ? UUID.fromString(delegatorStr) : null;

    AccessValidator.validate(
      userJson,
      List.of(DxRole.ORG_ADMIN.getRole()),
      List.of(
        DxScope.USER_MANAGEMENT.getScope(),
        DxScope.ORG_ADMIN_ACCESS.getScope()));

    UUID userId = UUID.fromString(user.subject());

    // -------- Resolve orgId --------
    Future<UUID> orgIdFuture;

    if (delegatorId == null) {
      orgIdFuture =
        userService
          .getUserInfoByID(userId)
          .compose(res -> {

            String orgIdStr = res.organisationId();

            if (orgIdStr == null || orgIdStr.trim().isEmpty()) {
              ctx.fail(
                new DxBadRequestException(
                  "The user is acting as a delegate. Please specify the delegatorId in the request")
              );
              return Future.failedFuture("Missing organisationId");
            }

            if (!Objects.equals(orgIdStr, orgIdparam)) {
              ctx.fail(
                new DxBadRequestException(
                  "The org id of the user and the query parameter are not same")
              );
              return Future.failedFuture("Wrong organisationId");
            }

            return Future.succeededFuture(UUID.fromString(orgIdStr));
          });

    } else {
      orgIdFuture =
        userService
          .getUserInfoByID(delegatorId)
          .compose(res -> {

            if (res == null) {
              ctx.fail(
                new DxBadRequestException(
                  "Delegator is not valid")
              );
              return Future.failedFuture("Delegator not valid");
            }

            String orgIdStr = res.organisationId();

            if (orgIdStr == null || orgIdStr.trim().isEmpty()) {
              ctx.fail(
                new DxBadRequestException(
                  "Delegator is not part of any organisation")
              );
              return Future.failedFuture("Delegator has no organisation");
            }

            if (!Objects.equals(orgIdStr, orgIdparam)) {
              ctx.fail(
                new DxBadRequestException(
                  "The org id of the delegator and the query parameter are not same")
              );
              return Future.failedFuture("Wrong organisationId");
            }

            return Future.succeededFuture(UUID.fromString(orgIdStr));
          });
    }

    // -------- Ownership + pagination + fetch --------
    orgIdFuture
      .compose(resolvedOrgId -> {
        if (delegatorId != null) {
          LOGGER.info("Checking the organisation ownership for delegator");
          return orgOwnershipValidator
            .validateOrgOwnership(delegatorId, List.of(orgIdparam))
            .map(v -> resolvedOrgId);
        }
        return Future.succeededFuture(resolvedOrgId);
      })
      .compose(resolvedOrgId -> {

        PaginatedRequest request =
          PaginationRequestBuilder.from(ctx)
            .ignoreDelegator(true)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_JOIN_REQUEST)
            .apiToDbMap(API_TO_DB_ORG_JOIN_REQUEST)
            .additionalFilters(
              Map.of(ORGANIZATION_ID, resolvedOrgId.toString()))
            .allowedTimeFields(Set.of(REQUESTED_AT))
            .defaultTimeField(REQUESTED_AT)
            .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(API_TO_DB_ORG_JOIN_REQUEST.keySet())
            .build();

        return organizationService
          .getOrganizationPendingJoinRequests(request)
          .compose(
            result ->
              userService
                .enrichWithUserRoles(
                  result.data(),
                  OrganizationJoinRequest::userId,
                  OrganizationJoinRequest::toJson)
                .map(
                  enriched ->
                    Map.entry(
                      enriched,
                      result.paginationInfo())));
      })
      .onSuccess(entry -> {

        ActivityAuditLogBuilder audit =
          OrganizationAuditHelper
            .buildViewJoinOrgRequestsAudit(ctx, orgId);

        RoutingContextHelper
          .setAuditingLogNew(ctx, audit);

        ResponseBuilder.sendSuccess(
          ctx,
          entry.getKey(),
          entry.getValue(),
          urnGenerator);
      })
      .onFailure(ctx::fail);
  }



  public void getUserJoinOrganisationRequests(RoutingContext ctx) {


    User user = ctx.user();
    UUID userId = UUID.fromString(ctx.user().subject());

    String delegatorIdStr = ctx.queryParams().get("delegatorId");
    UUID delegatorId = delegatorIdStr!=null?UUID.fromString(delegatorIdStr):null;

    if(delegatorId!=null)
      userId = delegatorId;

    UUID finalUserId = userId;

    organizationService
        .getOrganizationJoinRequestsByUser(finalUserId)
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
              ActivityAuditLogBuilder audit =
                  OrganizationAuditHelper.buildViewUserJoinOrgRequestsAudit(ctx, finalUserId);
              RoutingContextHelper.setAuditingLogNew(ctx, audit);

              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to fetch join organisation requests for user {}: {}",
                  finalUserId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  public void deleteUserJoinOrganisationRequests(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());
    String delegatorIdStr = ctx.queryParams().get("delegatorId");
    UUID delegatorId = delegatorIdStr!=null?UUID.fromString(delegatorIdStr):null;

    if(delegatorId!=null)
      userId = delegatorId;

    UUID finalUserId = userId;

    UUID requestId = UUID.fromString(ctx.pathParam("id"));

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

              if (!request.userId().equals(finalUserId)) {
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
                        ActivityAuditLogBuilder audit =
                            OrganizationAuditHelper.buildWithdrawJoinOrgRequestAudit(
                                ctx, requestId, request.organizationId());

                        RoutingContextHelper.setAuditingLogNew(ctx, audit);

                        ResponseBuilder.sendSuccess(
                            ctx, "Join organisation request deleted successfully", urnGenerator);
                        return Future.succeededFuture(true);
                      });
            })
        .onFailure(ctx::fail);
  }

  public void approveJoinOrganisationRequests(RoutingContext ctx) {

    User user = ctx.user();
    JsonObject userJson = user.principal();

    String orgIdparam = ctx.pathParam("org_id");
    String delegatorStr = ctx.queryParams().get("delegatorId");
    UUID delegatorId = delegatorStr != null ? UUID.fromString(delegatorStr) : null;

    // delegation requirement
    AccessValidator.validate(
      userJson,
      List.of(DxRole.ORG_ADMIN.getRole()),
      List.of(
        DxScope.USER_MANAGEMENT.getScope(),
        DxScope.ORG_ADMIN_ACCESS.getScope()
      )
    );

    JsonObject orgRequestJson = ctx.body().asJsonObject();
    UUID requestId = RequestHelper.getPathParamAsUUID(ctx, "req_id");
    Status status = Status.fromString(orgRequestJson.getString("status"));

    UUID userId = UUID.fromString(user.subject());

    Future<UUID> orgIdFuture;

    if (delegatorId == null) {
      orgIdFuture =
        userService
          .getUserInfoByID(userId)
          .compose(res -> {

            String orgIdStr = res.organisationId();

            if (orgIdStr == null || orgIdStr.trim().isEmpty()) {
              ctx.fail(
                new DxBadRequestException(
                  "The user is acting as a delegate. Please specify the delegatorId in the request")
              );
              return Future.failedFuture("Missing organisationId");
            }

            if (!Objects.equals(orgIdStr, orgIdparam)) {
              ctx.fail(
                new DxBadRequestException(
                  "The org id of the user and the query parameter are not same")
              );
              return Future.failedFuture("Wrong organisationId");
            }


            LOGGER.info("Resolved orgId: {}", orgIdStr);
            return Future.succeededFuture(UUID.fromString(orgIdStr));
          });

    } else {
      orgIdFuture =
        userService
          .getUserInfoByID(delegatorId)
          .compose(res -> {

            String orgIdStr = res.organisationId();

            if (orgIdStr == null || orgIdStr.trim().isEmpty()) {
              ctx.fail(
                new DxBadRequestException(
                  "Delegator is not part of any organisation")
              );
              return Future.failedFuture("Delegator has no organisation");
            }

            if (!Objects.equals(orgIdStr, orgIdparam)) {
              ctx.fail(
                new DxBadRequestException(
                  "The org id of the delegator and the query parameter are not same")
              );
              return Future.failedFuture("Wrong organisationId");
            }


            LOGGER.info("Resolved orgId: {}", orgIdStr);
            return Future.succeededFuture(UUID.fromString(orgIdStr));
          });
    }

    orgIdFuture
      .compose(
        orgId ->
          organizationService
            .updateOrganizationJoinRequestStatus(requestId, status)
            .onSuccess(
              approved -> {
                if (!approved) {
                  ctx.fail(
                    new DxNotFoundException("Request Not Found"));
                  return;
                }

                ActivityAuditLogBuilder audit;
                if (status == Status.GRANTED) {
                  audit =
                    OrganizationAuditHelper
                      .buildJoinOrgApproveAudit(
                        ctx,
                        requestId,
                        orgId,
                        userJson.getString(ORGANISATION_NAME));
                } else {
                  audit =
                    OrganizationAuditHelper
                      .buildJoinOrgRejectAudit(
                        ctx,
                        requestId,
                        orgId,
                        userJson.getString(ORGANISATION_NAME),
                        "Rejected by org admin");
                }

                RoutingContextHelper
                  .setAuditingLogNew(ctx, audit);

                emailComposer
                  .sendUserEmailForOrgJoinRequestApproval(
                    requestId, status)
                  .onFailure(
                    err ->
                      LOGGER.error(
                        "Email send failed", err));

                ResponseBuilder.sendSuccess(
                  ctx,
                  "Updated Organisation Join Request",
                  urnGenerator);
              }))
      .onFailure(ctx::fail);
  }


}
