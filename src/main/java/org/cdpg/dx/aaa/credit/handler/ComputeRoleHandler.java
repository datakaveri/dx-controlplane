package org.cdpg.dx.aaa.credit.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.credit.models.*;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.credit.util.CreditRequestAuditLogHelper;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.*;

import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.credit.util.Constants.*;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class ComputeRoleHandler {

  private static final Logger LOGGER = LogManager.getLogger(ComputeRoleHandler.class);
  private final CreditService creditService;
  private final EmailComposer emailComposer;
  private final UserService userService;
  private final OrganizationService organizationService;
  private final KeycloakUserService keycloakUserService;
  private final URNGenerator urnGenerator;

  public ComputeRoleHandler(CreditService creditService, EmailComposer emailComposer, UserService userService, OrganizationService organizationService, KeycloakUserService keycloakUserService, URNGenerator urnGenerator) {
    this.creditService = creditService;
    this.emailComposer = emailComposer;
    this.userService = userService;
    this.organizationService = organizationService;
    this.keycloakUserService = keycloakUserService;
    this.urnGenerator = urnGenerator;
  }


  public void createComputeRoleRequest(RoutingContext ctx) {

    if (ctx.body() == null || ctx.body().isEmpty() || ctx.body().asJsonObject() == null) {
      ctx.fail(new DxBadRequestException("Request Body is required and must be valid JSON."));
      return;
    }

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    JsonObject computeRoleJsonBody = ctx.body().asJsonObject();
    JsonObject additionalInfo = computeRoleJsonBody.getJsonObject("additional_info");

    keycloakUserService.getUserById(userId)
      .compose(keycloakUser -> {

        // Build request using Keycloak data
        ComputeRole computeRoleRequest = ComputeRole.fromJson(
          new JsonObject()
            .put("user_id", userId.toString())
            .put("user_name", keycloakUser.name())
            .put("additional_info", additionalInfo)
        );

        return creditService.getComputeRoleRequestByUserId(userId)
          .recover(err -> {
            if (err instanceof DxNotFoundException) {
              return Future.succeededFuture(null);
            }
            return Future.failedFuture(err);
          })
          .compose(existingComputeRole -> {
            if (existingComputeRole != null &&
              existingComputeRole.status().equalsIgnoreCase(Status.REJECTED.getStatus())) {

              return creditService.updateComputeRoleStatus(
                  existingComputeRole.id(),
                  Status.PENDING,
                  existingComputeRole.approvedBy()
                )
                .map(updated -> true);
            }
            return Future.succeededFuture(false);
          })
          .compose(updated -> {
            if (!updated) {
              return creditService.createComputeRoleRequest(computeRoleRequest)
                .onSuccess(requests -> {
                  LOGGER.info("Requests in compute : {}", requests.toJson());

                  UserActivityAuditLogBuilder auditLogBuilder =
                    CreditRequestAuditLogHelper.buildAudit(
                      ctx, requests.toJson(), CreditRequestAuditOperation.REQUEST_COMPUTE);

                  CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

                  ResponseBuilder.sendSuccess(ctx, requests, this.urnGenerator);
                  emailComposer.sendEmailForComputeRole(computeRoleRequest, user);
                })
                .mapEmpty();
            }
            return Future.succeededFuture();
          });
      })
      .onSuccess(v -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "POST",
          "Compute Role Request Created"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        ResponseBuilder.sendSuccess(
          ctx,
          "Compute Role Request created successfully",
          this.urnGenerator
        );
      })
      .onFailure(ctx::fail);
  }


  public void getAllComputeRequests(RoutingContext ctx) {
    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_COMPUTE_ROLE)
      .apiToDbMap(ALLOWED_FILTER_MAP_FOR_COMPUTE_ROLE)
      .additionalFilters(Map.of())
      .allowedTimeFields(Set.of(CREATED_AT))
      .defaultTimeField(CREATED_AT)
      .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
      .allowedSortFields(ALLOWED_FILTER_MAP_FOR_COMPUTE_ROLE.keySet())
      .build();


    creditService.getAllComputeRequests(request)
      .compose(result ->
        userService.enrichWithUserRoles(
          result.data(),
          ComputeRole::userId,
          ComputeRole::toJson
        ) .compose(organizationService::enrichWithUserInfo)
          .map(enrichedList -> Map.entry(enrichedList, result.paginationInfo()))
      )
      .onSuccess(entry -> {
        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx,new JsonObject(), CreditRequestAuditOperation.GET_COMPUTE_REQUESTS);
        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
        ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue(), this.urnGenerator);
      }).onFailure(ctx::fail);

  }

  public void updateComputeRoleStatus(RoutingContext ctx) {
    JsonObject creditRequestJson = ctx.body().asJsonObject();

    User user = ctx.user();
    UUID approvedBy = UUID.fromString(user.subject());
    Status status = Status.fromString(creditRequestJson.getString("status"));
    UUID requestId = RequestHelper.getPathParamAsUUID(ctx, "id");

    creditService.updateComputeRoleStatus(requestId, status, approvedBy)
      .onSuccess(updated -> {
//        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
//          RoutingContextHelper.getRequestPath(ctx), "PUT", "Compute Role Status Updated");
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx, new JsonObject().put(ID,requestId.toString()), CreditRequestAuditOperation.UPDATE);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(ctx, "Compute Role Status " + status.getStatus(), this.urnGenerator);
        Future<Void> future = emailComposer.sendUserEmailForComputeRoleApproval(requestId, status);
      })
      .onFailure(ctx::fail);
  }

  public void hasUserComputeAccess(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    creditService.hasUserComputeAccess(userId)
      .onSuccess(requests -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "GET", "Check User Compute Access");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, requests, this.urnGenerator);

      })
      .onFailure(ctx::fail);
  }


  public void getComputeRequests(RoutingContext ctx) {
    AuditLog auditLog = AuditingHelper.createAuditLog(
      ctx.user(),
      RoutingContextHelper.getRequestPath(ctx),
      "GET",
      "Get Pending Compute Request"
    );

    UUID userId = UUID.fromString(ctx.user().subject());

    creditService.getComputeRequestByUserId(userId)
      .compose(cr -> {
        if (cr == null) {
          ctx.fail(new DxBadRequestException("No pending compute request found"));
          return Future.failedFuture(new DxBadRequestException("No pending compute request found"));
        }

        return userService.enrichWithUserRoles(
          List.of(cr),
          ComputeRole::userId,
          ComputeRole::toJson
        ).map(list -> list.isEmpty() ? null : list.get(0));
      })
      .onSuccess(enriched -> {
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx, enriched , CreditRequestAuditOperation.GET_COMPUTE_REQUESTS);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
        ResponseBuilder.sendSuccess(ctx, enriched, this.urnGenerator);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to fetch pending compute request for user {}: {}", userId, err.getMessage());
        ctx.fail(err);
      });
  }

  public void deletePendingComputeRequests(RoutingContext ctx) {
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    String requestIdStr = ctx.pathParam("id");
    UUID requestId = UUID.fromString(requestIdStr);

    creditService.getComputeRequestById(requestId).compose(request -> {
        if (request == null) {
          return Future.failedFuture(new DxNotFoundException("Compute request not found"));
        }

        if (!request.userId().equals(userId)) {
          return Future.failedFuture(new DxForbiddenException("User is not authorized to delete this compute request"));
        }

        if (!request.status().equals(Status.PENDING.getStatus())) {
          return Future.failedFuture(new DxBadRequestException("Only pending compute requests can be deleted"));
        }

        return creditService.deletePendingComputeRequestById(requestId);
      })
      .onSuccess(deleted -> {
//        AuditLog auditLog = AuditingHelper.createAuditLog(
//          ctx.user(),
//          RoutingContextHelper.getRequestPath(ctx),
//          "DELETE",
//          "Deleted Pending Compute Request"
//        );
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx, new JsonObject().put(ID,requestIdStr) , CreditRequestAuditOperation.DELETE);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(ctx, "Pending Compute Request deleted successfully", urnGenerator);
      })
      .onFailure(ctx::fail);
  }

}
