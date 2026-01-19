package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.OrgOwnershipValidator;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
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
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

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
  private final OrgOwnershipValidator orgOwnershipValidator;

  public ProviderRoleHandler(
      OrganizationService organizationService,
      UserService userService,
      OrgOwnershipValidator orgOwnershipValidator,
      EmailComposer emailComposer,
      URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.userService = userService;
    this.orgOwnershipValidator = orgOwnershipValidator;
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

    String org = ctx.user().principal().getString("organisation_id");
    LOGGER.info("org : {}",org);
    LOGGER.info("principle : {}",ctx.user().principal());

    UUID userId = UUID.fromString(user.subject());

    // Resolve orgId asynchronously
    Future<UUID> orgIdFuture =
      userService
        .getUserInfoByID(userId)
        .compose(res -> {

          String orgIdStr = res.organisationId();

          if (orgIdStr == null || orgIdStr.trim().isEmpty()) {
            ctx.fail(
              new DxForbiddenException("User is not part any organisation")
            );
            return Future.failedFuture("Missing organisationId");
          }

          LOGGER.info("Resolved orgId: {}", orgIdStr);
          return Future.succeededFuture(UUID.fromString(orgIdStr));
        });

    orgIdFuture
      .compose(orgId -> {

        JsonObject req =
          new JsonObject()
            .put("user_id", user.subject())
            .put("organization_id", orgId.toString());

        ProviderRoleRequest providerRoleRequest =
          ProviderRoleRequest.fromJson(req);

        return organizationService
          .createProviderRequest(providerRoleRequest)
          .onSuccess(
            requests -> {

              ActivityAuditLogBuilder audit =
                OrganizationAuditHelper
                  .buildProviderRoleRequestSubmitAudit(
                    ctx, requests.id(), orgId);

              RoutingContextHelper.setAuditingLogNew(ctx, audit);

              ResponseBuilder.sendSuccess(
                ctx, "Created Request", urnGenerator);

              emailComposer
                .sendEmailForProviderRole(providerRoleRequest, user);
            }
          );
      })
      .onFailure(ctx::fail);
  }


  public void updateProviderRequest(RoutingContext ctx) {

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
      userJson,
      List.of(DxRole.ORG_ADMIN.getRole()),
      List.of(
        DxScope.USER_MANAGEMENT.getScope(),
        DxScope.ORG_ADMIN_ACCESS.getScope()
      )
    );

    String delegatorStr = ctx.queryParams().get("delegatorId");
    UUID delegatorId = delegatorStr != null ? UUID.fromString(delegatorStr) : null;

    JsonObject orgRequestJson = ctx.body().asJsonObject();
    UUID reqId = RequestHelper.getPathParamAsUUID(ctx, "id");
    Status status = Status.fromString(orgRequestJson.getString("status"));

    UUID userId = UUID.fromString(user.subject());

    // Resolve orgId (fixed)
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


            LOGGER.info("Resolved orgId: {}", orgIdStr);
            return Future.succeededFuture(UUID.fromString(orgIdStr));
          });
    }

    orgIdFuture
      // ownership validation (only for delegation)
      .compose(
        orgId -> {
          if (delegatorId != null) {
            return orgOwnershipValidator
              .validateOrgOwnership(
                delegatorId, List.of(orgId.toString()))
              .map(v -> orgId);
          }
          return Future.succeededFuture(orgId);
        })
      // update request status
      .compose(
        orgId ->
          organizationService
            .updateProviderRequestStatus(reqId, status)
            .map(v -> orgId))
      .onSuccess(
        orgId -> {

          ActivityAuditLogBuilder audit;
          if (status == Status.GRANTED) {
            audit =
              OrganizationAuditHelper
                .buildProviderRoleApproveAudit(
                  ctx, reqId, orgId);
          } else {
            audit =
              OrganizationAuditHelper
                .buildProviderRoleRejectAudit(
                  ctx, reqId, orgId, "Rejected by admin");
          }

          RoutingContextHelper.setAuditingLogNew(ctx, audit);

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

    User user = ctx.user();
    JsonObject userJson = user.principal();

    LOGGER.info("userInfo: {}", userJson);
    UUID userId = UUID.fromString(user.subject());

    String delegatorStr = ctx.queryParams().get("delegatorId");
    UUID delegatorId = delegatorStr != null ? UUID.fromString(delegatorStr) : null;

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


            LOGGER.info("Resolved orgId: {}", orgIdStr);
            return Future.succeededFuture(UUID.fromString(orgIdStr));
          });
    }

    orgIdFuture
      .compose(resOrgId -> {

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

        return organizationService.getAllPendingProviderRoleRequests(request)
          .map(providerResult -> Map.entry(providerResult, resOrgId));
      })
      .compose(entry -> {

        PaginatedResult<ProviderRoleRequest> providerResult = entry.getKey();
        UUID orgId = entry.getValue();

        return organizationService.getOrganizationJoinRequestsByOrgId(orgId)
          .map(joinRequests -> Map.entry(providerResult, joinRequests));
      })
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
              LOGGER.info("The json is : {}",json);
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
      .onSuccess(entry ->
        ResponseBuilder.sendSuccess(
          ctx,
          entry.getKey(),
          entry.getValue(),
          urnGenerator
        )
      )
      .onFailure(ctx::fail);
}

    public void deleteUserProviderRoleRequest(RoutingContext ctx) {

    UUID userId = UUID.fromString(ctx.user().subject());
    String delegatorStr = ctx.queryParams().get("delegatorId");
    UUID delegatorId = delegatorStr!=null ? UUID.fromString(delegatorStr): null;

    if(delegatorId!=null)
     userId = delegatorId;


    UUID finalUserId = userId;

    organizationService
        .getProviderRoleRequestByUserId(finalUserId)
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(
                    new DxNotFoundException(
                        "No provider role request found for userId: " + finalUserId));
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
                  finalUserId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  public void getProviderRoleRequest(RoutingContext ctx) {
    UUID userId = UUID.fromString(ctx.user().subject());
    JsonObject userJson = ctx.user().principal();

    String delegatorStr = ctx.queryParams().get("delegatorId");
    UUID delegatorId = delegatorStr!=null ? UUID.fromString(delegatorStr): null;

    if(delegatorId!=null)
      userId = delegatorId;


    UUID finalUserId = userId;

    organizationService
        .getProviderRoleRequestByUserId(finalUserId) // Future<ProviderRoleRequest>
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(
                    new DxNotFoundException(
                        "No provider role request found for userId: " + finalUserId));
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
                  finalUserId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  public void createProviderRole(RoutingContext ctx) {

    JsonObject userJson = ctx.user().principal();

    AccessValidator.validate(
      userJson,
      List.of(DxRole.ORG_ADMIN.getRole()),
      List.of(
        DxScope.USER_MANAGEMENT.getScope(),
        DxScope.ORG_ADMIN_ACCESS.getScope()
      )
    );

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
