package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.OrgOwnershipValidator;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
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
import org.cdpg.dx.common.util.DelegatorResolver;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_ORG_JOIN_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.REQUESTED_AT;
import static org.cdpg.dx.aaa.organization.models.Status.WITHDRAWN;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;
import static org.cdpg.dx.keycloak.config.KeycloakConstants.ORGANISATION_NAME;

public class OrganizationJoinRequestHandler {
  private static final Logger LOGGER = LogManager.getLogger(OrganizationJoinRequestHandler.class);

  private final OrganizationService organizationService;
  private final UserService userService;
  private final EmailComposer emailComposer;
  private final URNGenerator urnGenerator;
  private final OrgOwnershipValidator orgOwnershipValidator;
  private final KeycloakUserService keycloakUserService;

  public OrganizationJoinRequestHandler(
      OrganizationService organizationService,
      OrgOwnershipValidator orgOwnershipValidator,
      UserService userService,
      KeycloakUserService keycloakUserService,
      EmailComposer emailComposer,
      URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.orgOwnershipValidator = orgOwnershipValidator;
    this.userService = userService;
    this.emailComposer = emailComposer;
    this.urnGenerator = urnGenerator;
    this.keycloakUserService = keycloakUserService;

  }

  public void joinOrganisationRequest(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    JsonObject orgRequestJson = ctx.body().asJsonObject();

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    keycloakUserService.getUserById(userId)
      .compose(keycloakUser -> {

        // ONLY change: user_name from Keycloak
        orgRequestJson.put("user_id", user.subject());
        orgRequestJson.put("user_name", keycloakUser.name());
        orgRequestJson.put("organization_id", orgId.toString());

        OrganizationJoinRequest organizationJoinRequest =
          OrganizationJoinRequest.fromJson(orgRequestJson);

        String officialEmailStr = organizationJoinRequest.officialEmail();

        return organizationService
          .getAllOrganizationJoinRequests()
          .compose(joinRequests -> {

            for (OrganizationJoinRequest request : joinRequests) {

              // Same user + same org + active request
              if (
                request.organizationId().equals(orgId)
                  && request.userId().equals(userId)
                  && !request.status().equals(Status.WITHDRAWN.getStatus())
              ) {
                return Future.failedFuture(
                  new DxConflictException(
                    "User already has a pending/granted join request for this organization"
                  )
                );
              }

              // Same official email already in use
              if (
                request.officialEmail().equals(officialEmailStr)
                  && !Set.of(
                  Status.REJECTED.getStatus(),
                  Status.WITHDRAWN.getStatus()
                ).contains(request.status())
              ) {
                return Future.failedFuture(
                  new DxConflictException("This email has been used already!")
                );
              }
            }

            return organizationService.joinOrganizationRequest(organizationJoinRequest);
          });
      })
      .onSuccess(createdRequest -> {

        UserActivityAuditLogBuilder auditLogBuilder =
          OrganizationAuditHelper.buildOrganisationAudit(
            ctx, createdRequest.toJson(), OrganisationAuditOperation.REQUEST_ORG_JOIN);
        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
        ResponseBuilder.sendSuccess(ctx, "Created Join request", urnGenerator);

        emailComposer.sendEmailForJoiningOrg(createdRequest, user);
      })
      .onFailure(ctx::fail);
  }

  public void getJoinOrganisationRequests(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    User user = ctx.user();
    JsonObject userJson = user.principal();

    String orgIdparam = ctx.pathParam("id");
    UUID delegatorId = DelegatorResolver.getDelegatorId(ctx);

//    AccessValidator.validate(
//      userJson,
//      List.of(DxRole.ORG_ADMIN.getRole()),
//      List.of(
//        DxScope.USER_MANAGEMENT.getScope(),
//        DxScope.ORG_ADMIN_ACCESS.getScope()));

//    UUID userId = UUID.fromString(user.subject());

    // -------- Resolve orgId --------
    Future<UUID> orgIdFuture = DelegatorResolver.resolveOrgId(ctx, userService, orgIdparam);

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

        UserActivityAuditLogBuilder auditLogBuilder =
          OrganizationAuditHelper.buildOrganisationAudit(
            ctx, new JsonObject(), OrganisationAuditOperation.GET);
        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(
          ctx,
          entry.getKey(),
          entry.getValue(),
          urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  //TODO: scopes for user , refactor



  public void getUserJoinOrganisationRequests(RoutingContext ctx) {


    User user = ctx.user();
    UUID userId = DelegatorResolver.resolveActingUserId(ctx);

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
              UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                  ctx, new JsonObject(), OrganisationAuditOperation.GET);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

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

  public void deleteUserJoinOrganisationRequests(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = DelegatorResolver.resolveActingUserId(ctx);

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
                        UserActivityAuditLogBuilder auditLogBuilder =
                          OrganizationAuditHelper.buildOrganisationAudit(
                            ctx, new JsonObject().put(ID,requestId.toString()), OrganisationAuditOperation.DELETE_PENDING_ORG_JOIN_REQUEST);
                        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

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

    // delegation requirement
//    AccessValidator.validate(
//      userJson,
//      List.of(DxRole.ORG_ADMIN.getRole()),
//      List.of(
//        DxScope.USER_MANAGEMENT.getScope(),
//        DxScope.ORG_ADMIN_ACCESS.getScope()
//      )
//    );

    JsonObject orgRequestJson = ctx.body().asJsonObject();
    UUID requestId = RequestHelper.getPathParamAsUUID(ctx, "req_id");
    Status status = Status.fromString(orgRequestJson.getString("status"));

    UUID userId = UUID.fromString(user.subject());

    DelegatorResolver.resolveOrgId(ctx, userService, orgIdparam)
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

                UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                    ctx, new JsonObject().put(ID,requestId.toString()), OrganisationAuditOperation.UPDATE_ORG_JOIN_REQUEST);
                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

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

  public void withdrawJoinRequest(RoutingContext ctx) {

    User user = ctx.user();
    JsonObject userJson = user.principal();

    UUID userId = DelegatorResolver.resolveActingUserId(ctx);
    UUID delegatorId = DelegatorResolver.getDelegatorId(ctx);
    UUID orgJoinReqId = UUID.fromString(ctx.pathParam("id"));

    Future<Void> validationFuture;

    if (delegatorId != null) {

      validationFuture =
        userService
          .getUserInfoByID(delegatorId)
          .compose(res -> {

            if (res == null) {
              return Future.failedFuture(
                new DxBadRequestException("Delegator is not valid"));
            }

            String orgIdStr = res.organisationId();
            if (orgIdStr == null || orgIdStr.isBlank()) {
              return Future.failedFuture(
                new DxBadRequestException("Delegator is not part of any organisation"));
            }

            UUID orgId = UUID.fromString(orgIdStr);
            LOGGER.info("Resolved orgId: {}", orgId);

            return organizationService
              .getOrganizationJoinRequestById(orgJoinReqId)
              .compose(request -> {

                if (!request.organizationId().equals(orgId)) {
                  return Future.failedFuture(
                    new DxBadRequestException(
                      "The delegate does not have access to withdraw the join request as the delegator is not the owner of the organization"));
                }

                return Future.succeededFuture();
              });
          });

    } else {
      // No delegator → no extra validation needed
      validationFuture = Future.succeededFuture();
    }

    validationFuture.compose(v -> organizationService.withdrawJoinRequest(userId, orgJoinReqId))
      .onSuccess(res -> {

        UserActivityAuditLogBuilder auditLogBuilder =
          OrganizationAuditHelper.buildOrganisationAudit(
            ctx, new JsonObject().put(ID,orgJoinReqId.toString()), OrganisationAuditOperation.WITHDRAW_PENDING_ORG_JOIN_REQUEST);
        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(
          ctx,
          "Updated Organisation Join Request",
          urnGenerator);
      })
      .onFailure(ctx::fail);
  }



}
