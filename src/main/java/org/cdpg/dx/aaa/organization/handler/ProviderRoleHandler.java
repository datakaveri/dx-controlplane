package org.cdpg.dx.aaa.organization.handler;

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
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.ProviderRoleRequest;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auth.authentication.util.AccessValidator;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.util.RoutingContextHelper;

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

    User user = ctx.user();
    LOGGER.debug("User: {}", user);
    if (user == null || user.subject() == null || user.principal() == null) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    String userId = user.subject();
    String orgID = user.principal().getString("organisation_id");

    if (userId == null || userId.isEmpty()) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    if (orgID == null || orgID.isEmpty()) {
      ctx.fail(new DxForbiddenException("User is not part any organisation"));
      return;
    }

    JsonObject req = new JsonObject().put("user_id", user.subject()).put("organization_id", orgID);

    ProviderRoleRequest providerRoleRequest = ProviderRoleRequest.fromJson(req);

    organizationService
        .createProviderRequest(providerRoleRequest)
        .onSuccess(
            requests -> {
              ActivityAuditLogBuilder audit =
                  OrganizationAuditHelper.buildProviderRoleRequestSubmitAudit(
                      ctx, requests.id(), UUID.fromString(orgID));

              RoutingContextHelper.setAuditingLogNew(ctx, audit);

              ResponseBuilder.sendSuccess(ctx, "Created Request", urnGenerator);
              Future<Void> future =
                  emailComposer.sendEmailForProviderRole(providerRoleRequest, user);
            })
        .onFailure(ctx::fail);
  }

  public void updateProviderRequest(RoutingContext ctx) {

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.ORG_ADMIN.getRole()),
        List.of(DxScope.USER_MANAGEMENT.getScope(),DxScope.ORG_ADMIN_ACCESS.getScope()));

    JsonObject OrgRequestJson = ctx.body().asJsonObject();
    UUID reqId = RequestHelper.getPathParamAsUUID(ctx, "id");
    Status status = Status.fromString(OrgRequestJson.getString("status"));

    organizationService
        .updateProviderRequestStatus(reqId, status)
        .onSuccess(
            requests -> {
              ActivityAuditLogBuilder audit;

              if (status == Status.GRANTED) {
                audit =
                    OrganizationAuditHelper.buildProviderRoleApproveAudit(
                        ctx, reqId, UUID.fromString(userJson.getString(ORGANISATION_ID)));

              } else {
                audit =
                    OrganizationAuditHelper.buildProviderRoleRejectAudit(
                        ctx,
                        reqId,
                        UUID.fromString(userJson.getString(ORGANISATION_ID)),
                        "Rejected by admin");
              }

              RoutingContextHelper.setAuditingLogNew(ctx, audit);

              ResponseBuilder.sendSuccess(ctx, "Provider role updated", urnGenerator);
              Future<Void> future =
                  emailComposer.sendUserEmailForProviderRoleApproval(reqId, status);
            })
        .onFailure(ctx::fail);
  }



  public void getProviderRequest(RoutingContext ctx) {

    User user = ctx.user();
    JsonObject userJson = user.principal();

    LOGGER.info("userInfo: {}",userJson);

    UUID userId = UUID.fromString(user.subject());

    String orgIdStr = ctx.pathParam("id");
    if (orgIdStr == null || orgIdStr.isBlank()) {
      ctx.fail(new DxForbiddenException("Organization ID is required"));
      return;
    }

    UUID requestedOrgId = UUID.fromString(orgIdStr);

    UUID delegatorId = null;
    if (userJson.containsKey("did")) {
      delegatorId = UUID.fromString(userJson.getString("did"));
    }

    LOGGER.info("Fetching provider requests for org {} by user {}", requestedOrgId, userId);

     //Resolve effective owner

    resolveEffectiveOwner(userId, delegatorId, requestedOrgId, userJson)
      .compose(effectiveOwnerId -> {

        // Build paginated request FOR REQUESTED ORG ONLY

        PaginatedRequest request = PaginationRequestBuilder.from(ctx)
          .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_PROVIDER_ROLE_REQUEST)
          .apiToDbMap(API_TO_DB_PROVIDER_ROLE_REQUEST)
          .additionalFilters(Map.of(ORGANIZATION_ID, requestedOrgId.toString()))
          .allowedTimeFields(Set.of(CREATED_AT))
          .defaultTimeField(CREATED_AT)
          .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
          .allowedSortFields(API_TO_DB_PROVIDER_ROLE_REQUEST.keySet())
          .build();

        return organizationService.getAllPendingProviderRoleRequests(request);
      })
      .compose(requests ->
        userService.enrichWithUserRoles(
          requests.data(),
          ProviderRoleRequest::userId,
          ProviderRoleRequest::toJson
        ).map(enriched ->
          Map.entry(enriched, requests.paginationInfo())
        )
      )
      .onSuccess(entry -> {
        ResponseBuilder.sendSuccess(
          ctx,
          entry.getKey(),
          entry.getValue(),
          urnGenerator
        );
      })
      .onFailure(ctx::fail);
  }

  private Future<UUID> resolveEffectiveOwner(
    UUID userId,
    UUID delegatorId,
    UUID requestedOrgId,
    JsonObject userJson
  ) {

    LOGGER.info("requestedOrgId: {}",requestedOrgId);
    LOGGER.info("delegatorId: {}",delegatorId);
    LOGGER.info("delegateId: {}",userId);


    // Case 1: User is direct org admin
    return userService.getUserInfoByID(userId)
      .compose(orgUser -> {
        if (orgUser != null && requestedOrgId.toString().equals(orgUser.organisationId())) {
          LOGGER.info("User {} is direct owner of org {}", userId, requestedOrgId);
          return Future.succeededFuture(userId);
        }

        // Case 2: Delegated access
        if (delegatorId == null) {
          return Future.failedFuture(
            new DxForbiddenException("User is neither owner nor delegate")
          );
        }

        // Scope check for delegated access
        AccessValidator.validate(
          userJson,
          List.of(DxRole.ORG_ADMIN.getRole()),
          List.of(DxScope.USER_MANAGEMENT.getScope(),DxScope.ORG_ADMIN_ACCESS.getScope())
        );

        return userService.getUserInfoByID(delegatorId)
          .compose(delegatorOrgUser -> {

            // Case 2a: Delegator is COS admin (global authority)
            if (delegatorOrgUser != null
              && delegatorOrgUser.roles().contains(DxRole.COS_ADMIN.toString())) {

              LOGGER.info(
                "User {} acting as delegate for COS admin {} on org {}",
                userId, delegatorId, requestedOrgId
              );

              return Future.succeededFuture(delegatorId);
            }

            // Case 2b: Delegator owns requested org
            if (delegatorOrgUser != null
              && requestedOrgId.toString().equals(delegatorOrgUser.organisationId())) {

              LOGGER.info(
                "User {} acting as delegate for org owner {} on org {}",
                userId, delegatorId, requestedOrgId
              );

              return Future.succeededFuture(delegatorId);
            }

            // Not allowed
            return Future.failedFuture(
              new DxForbiddenException(
                "Delegator does not have authority over requested organization"
              )
            );
          });
      });
  }


  public void deleteUserProviderRoleRequest(RoutingContext ctx) {

    UUID userId = UUID.fromString(ctx.user().subject());

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
                        return Future.succeededFuture(request); // <-- keep request
                      });
            })
        .onSuccess(
            request -> {
              ActivityAuditLogBuilder audit =
                  OrganizationAuditHelper.buildProviderRoleWithdrawAudit(
                      ctx, request.id(), request.orgId());

              RoutingContextHelper.setAuditingLogNew(ctx, audit);

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
    UUID userId = UUID.fromString(ctx.user().subject());

    organizationService
        .getProviderRoleRequestByUserId(userId) // Future<ProviderRoleRequest>
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
          ActivityAuditLogBuilder audit =
            OrganizationAuditHelper.buildProviderRoleGrantedAudit(
              ctx, providerRoleRequest.id(), providerRoleRequest.orgId());
          RoutingContextHelper.setAuditingLogNew(ctx, audit);

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
