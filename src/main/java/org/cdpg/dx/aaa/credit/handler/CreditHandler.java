package org.cdpg.dx.aaa.credit.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.CreditTransaction;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.models.ProviderRoleRequest;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;

import java.util.*;

import static org.cdpg.dx.aaa.credit.util.Constants.*;
import static org.cdpg.dx.aaa.credit.models.Status.GRANTED;
import static org.cdpg.dx.aaa.credit.util.Constants.ALLOWED_FILTER_MAP_FOR_COMPUTE_ROLE;
import static org.cdpg.dx.aaa.credit.util.Constants.CREATED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class CreditHandler {

  private static final Logger LOGGER = LogManager.getLogger(CreditHandler.class);
  private final CreditService creditService;
  private final EmailComposer emailComposer;
  private final UserService userService;


  public CreditHandler(CreditService creditService, EmailComposer emailComposer, UserService userService) {
    this.creditService = creditService;
    this.emailComposer = emailComposer;
    this.userService = userService;
  }


  public void createCreditRequest(RoutingContext ctx) {
    JsonObject creditRequestJson = ctx.body().asJsonObject();

    CreditRequest creditRequest;
    User user = ctx.user();
    creditRequestJson.put("user_id", user.subject());

    String userName = user.principal().getString("name");
    creditRequestJson.put("user_name", userName);

    creditRequest = CreditRequest.fromJson(creditRequestJson);

    creditService.createCreditRequest(creditRequest)
      .onSuccess(requests ->
      {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "POST", "Credit Request Created");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, requests);
        Future<Void> future = emailComposer.sendEmailForCreditRequest(user);



      })
      .onFailure(ctx::fail);
  }

  public void getAllPendingCreditRequests(RoutingContext ctx) {

    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST)
      .apiToDbMap(ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST)
      .allowedTimeFields(Set.of(CREATED_AT))
      .defaultTimeField(REQUESTED_AT)
      .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
      .allowedSortFields(ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST.keySet())
      .build();

    AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
      RoutingContextHelper.getRequestPath(ctx), "GET", "Get All Credit Requests");

    creditService.getAllCreditRequests(request)
      .onSuccess(result -> {
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx,  result.data(), result.paginationInfo());
      })
      .onFailure(ctx::fail);

  }


  public void getBalance(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    creditService.getBalance(userId)
      .onSuccess(balance -> {
        ResponseBuilder.sendSuccess(ctx,new JsonObject(Map.of("balance", balance)));
      })
      .onFailure(ctx::fail);
  }

  public void getBalanceofUser(RoutingContext ctx) {
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "id");
    creditService.getBalance(userId)
      .onSuccess(balance -> {
        ResponseBuilder.sendSuccess(ctx,new JsonObject(Map.of("user_id", userId, "balance", balance)));
      })
      .onFailure(ctx::fail);
  }


  public void updateCreditRequestStatus(RoutingContext ctx) {

    JsonObject creditRequestJson = ctx.body().asJsonObject();

    JsonObject responseObject = creditRequestJson.copy();
    responseObject.remove("status");

    User user = ctx.user();
    UUID transactedBy = UUID.fromString(user.subject());
    Status status = Status.fromString(creditRequestJson.getString("status"));
    UUID requestId = UUID.fromString(creditRequestJson.getString("id"));

    Double amount = null;

    if (status == Status.GRANTED && creditRequestJson.getValue("amount") == null) {
      throw new DxBadRequestException("Amount is required for GRANTED status");
    }


    amount = creditRequestJson.getDouble("amount");

    creditService.updateCreditRequestStatus( requestId, status, transactedBy,amount)
      .onSuccess(transaction -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "PUT", "Credit Request Status Updated");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx,  transaction);
        Future<Void> future = emailComposer.sendUserEmailForCreditApproval(requestId,status);
      }).onFailure(ctx::fail);

  }


  public void deductCredits(RoutingContext ctx) {
    JsonObject creditDeductionJson = ctx.body().asJsonObject();

    User user = ctx.user();
    UUID transactedBy = UUID.fromString(user.subject());
    creditDeductionJson.put("transacted_by", transactedBy.toString());

    JsonObject responseObject = creditDeductionJson.copy();

    // pass userId and userName from json
    CreditTransaction creditTransaction = CreditTransaction.fromJson(creditDeductionJson);
    creditService.deductCredits(creditTransaction)
      .onSuccess(res -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "PUT", "Credits Deducted");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, res);
      })
      .onFailure(ctx::fail);
  }

  public void addCredits(RoutingContext ctx) {
    JsonObject creditAdditionJson = ctx.body().asJsonObject();

    User user = ctx.user();
    UUID transactedBy = UUID.fromString(user.subject());
    creditAdditionJson.put("transacted_by", transactedBy.toString());

    JsonObject responseObject = creditAdditionJson.copy();

    // pass userId and userName from json
    CreditTransaction creditTransaction = CreditTransaction.fromJson(creditAdditionJson);
    creditService.addCredits(creditTransaction)
      .onSuccess(res -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "PUT", "Credits Added");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, res);
      })
      .onFailure(ctx::fail);
  }

  public void createComputeRoleRequest(RoutingContext ctx) {

    User user = ctx.user();
    String userID = user.subject();
    String userName = user.principal().getString("name");

    JsonObject computeRoleJsonBody = ctx.body().asJsonObject();
    System.out.println("Additional Info: " + computeRoleJsonBody);

    JsonObject additionalInfo = computeRoleJsonBody.getJsonObject("additional_info");

    ComputeRole computeRoleRequest = ComputeRole.fromJson(new JsonObject().put("user_id", userID).put("user_name", userName).put("additional_info", additionalInfo));

    creditService.getComputeRoleRequestByUserId(UUID.fromString(userID))
      .recover(err -> {
        if (err instanceof DxNotFoundException) {
          return Future.succeededFuture(null);
        }
        // For other errors, propagate
        return Future.failedFuture(err);
      })
      .compose(existingComputeRole -> {
        System.out.println("here in compose block");
        if (existingComputeRole != null && existingComputeRole.status().equalsIgnoreCase(Status.REJECTED.getStatus())) {
          return creditService.updateComputeRoleStatus(existingComputeRole.id(), Status.PENDING, existingComputeRole.approvedBy())
            .map(updated -> true);
        } else {
          // Otherwise, create a new compute role request
          return Future.succeededFuture(false);
        }
      })
      .compose(updated -> {
        System.out.println("here in compose block after update" + updated);
        if (!updated) {
          return creditService.createComputeRoleRequest(computeRoleRequest)
            .onSuccess(requests -> {
              ResponseBuilder.sendSuccess(ctx, requests);
              emailComposer.sendEmailForComputeRole(computeRoleRequest, user);
            })
            .onFailure(ctx::fail)
            .mapEmpty();
        } else {
          return Future.succeededFuture();
        }
      })
      .onSuccess(v -> {
        System.out.println("here in onSuccess block");
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "POST", "Compute Role Request Created");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, "Compute Role Request created successfully");
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
      .compose(result->
        userService.enrichWithUserRoles(
          result.data(),
          ComputeRole::userId,
          ComputeRole::toJson
        ).map(enrichedList -> Map.entry(enrichedList, result.paginationInfo()))
      )
      .onSuccess(entry -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "GET",
          "Get Compute Role Requests"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue());
      }).onFailure(ctx::fail);

  }

  public void updateComputeRoleStatus(RoutingContext ctx) {

    JsonObject creditRequestJson = ctx.body().asJsonObject();

    User user = ctx.user();
    UUID approvedBy = UUID.fromString(user.subject());
    Status status = Status.fromString(creditRequestJson.getString("status"));
    UUID requestId = RequestHelper.getPathParamAsUUID(ctx,"id");

    creditService.updateComputeRoleStatus( requestId, status, approvedBy)
      .onSuccess(updated -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "PUT", "Compute Role Status Updated");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, "Compute Role Status " + status.getStatus());
        Future<Void> future = emailComposer.sendUserEmailForComputeRoleApproval(requestId,status);
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
        ResponseBuilder.sendSuccess(ctx, requests);

      })
      .onFailure(ctx::fail);
  }

}

