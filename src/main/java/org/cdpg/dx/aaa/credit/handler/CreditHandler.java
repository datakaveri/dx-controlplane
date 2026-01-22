package org.cdpg.dx.aaa.credit.handler;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auth.authentication.util.AccessValidator;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.cdpg.dx.aaa.credit.util.Constants.*;
import static org.cdpg.dx.aaa.credit.models.Status.GRANTED;
import static org.cdpg.dx.aaa.credit.util.Constants.ALLOWED_FILTER_MAP_FOR_COMPUTE_ROLE;
import static org.cdpg.dx.aaa.credit.util.Constants.CREATED_AT;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class CreditHandler {

  private static final Logger LOGGER = LogManager.getLogger(CreditHandler.class);
  private final CreditService creditService;
  private final EmailComposer emailComposer;
  private final UserService userService;
  private final URNGenerator urnGenerator;
  private final OrganizationService organizationService;


  public CreditHandler(CreditService creditService, EmailComposer emailComposer, UserService userService, OrganizationService organizationService, URNGenerator urnGenerator) {
    this.creditService = creditService;
    this.emailComposer = emailComposer;
    this.userService = userService;
    this.organizationService = organizationService;
    this.urnGenerator = urnGenerator;
  }


  public void createCreditRequest(RoutingContext ctx) {




    JsonObject creditRequestJson = Optional.ofNullable(ctx.body().asJsonObject())
      .orElse(new JsonObject());


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
        ResponseBuilder.sendSuccess(ctx, requests, this.urnGenerator);
        emailComposer.sendEmailForCreditRequest(user);
      })
      .onFailure(ctx::fail);
  }

  public void getCreditRequests(RoutingContext ctx) {


    User dxUser = ctx.user();
    JsonObject userJson = dxUser.principal();

    AccessValidator.validate(
      userJson,
      List.of( // primary roles (no scope check)
        DxRole.COS_ADMIN.getRole()),
      List.of(DxScope.CREDIT_MANAGEMENT.getScope(),DxScope.COS_ADMIN_ACCESS.getScope())
    );

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
        List<CreditRequest> creditRequests = result.data();

        List<Future> futures = creditRequests.stream()
          .map(cr -> {
            UUID userId = cr.userId();
            Promise<JsonObject> promise = Promise.promise();

            Future<JsonObject> balanceFuture = creditService.getBalance(userId)
              .map(res -> new JsonObject().put("balance", res.getString("balance")))
              .otherwise(new JsonObject().put("balance", 0.0));

            Future<JsonObject> expiryFuture = creditService.getExpirationDateByUserId(userId)
              .map(res -> new JsonObject().put("expirationDate", res.expirationDate().toString()))
              .otherwise(new JsonObject().put("expirationDate", (String) null));

            CompositeFuture.all(balanceFuture, expiryFuture)
              .onSuccess(cf -> {
                JsonObject creditRequestJson = JsonObject.mapFrom(cr)
                  .mergeIn(cf.resultAt(0)) // balance
                  .mergeIn(cf.resultAt(1)); // expiration date
                promise.complete(creditRequestJson);
              })
              .onFailure(err -> {
                LOGGER.warn("Failed to get extra info for user {}: {}", userId, err.getMessage());
                JsonObject creditRequestJson = JsonObject.mapFrom(cr)
                  .put("balance", 0.0)
                  .put("expirationDate", (String) null);
                promise.complete(creditRequestJson);
              });

            return promise.future();
          })
          .collect(Collectors.toList());

        CompositeFuture.all(futures)
          .onSuccess(cf -> {
            List<JsonObject> enrichedList = cf.list();
            RoutingContextHelper.setAuditingLog(ctx, auditLog);
            ResponseBuilder.sendSuccess(ctx, enrichedList, result.paginationInfo(), this.urnGenerator);
          })
          .onFailure(ctx::fail);
      })
      .onFailure(ctx::fail);
  }


  public void getBalance(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    creditService.getBalance(userId)
      .onSuccess(balance -> {
        ResponseBuilder.sendSuccess(ctx, new JsonObject(Map.of("balance", balance)), this.urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void getBalanceofUser(RoutingContext ctx) {

    User dxUser = ctx.user();
    JsonObject userJson = dxUser.principal();

    AccessValidator.validate(
      userJson,
      List.of( // primary roles (no scope check)
        DxRole.COS_ADMIN.getRole()),
      List.of(DxScope.CREDIT_MANAGEMENT.getScope(),DxScope.COS_ADMIN_ACCESS.getScope())
    );

    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "id");
    creditService.getBalance(userId)
      .onSuccess(res -> {
        ResponseBuilder.sendSuccess(ctx, new JsonObject(Map.of("user_id", userId, "balance", res.getDouble("balance"))), this.urnGenerator);
      })
      .onFailure(ctx::fail);
  }


  public void updateCreditRequestStatus(RoutingContext ctx) {


    User dxUser = ctx.user();
    JsonObject userJson = dxUser.principal();

    AccessValidator.validate(
      userJson,
      List.of( // primary roles (no scope check)
        DxRole.COS_ADMIN.getRole()),
      List.of(DxScope.CREDIT_MANAGEMENT.getScope(),DxScope.COS_ADMIN_ACCESS.getScope())
    );

    JsonObject creditRequestJson = ctx.body().asJsonObject();

    JsonObject responseObject = creditRequestJson.copy();
    responseObject.remove("status");

    User user = ctx.user();
    UUID transactedBy = UUID.fromString(user.subject());
    Status status = Status.fromString(creditRequestJson.getString("status"));
    UUID requestId = UUID.fromString(creditRequestJson.getString("id"));

    if (status == Status.GRANTED && creditRequestJson.getValue("amount") == null) {
      throw new DxBadRequestException("Amount is required for GRANTED status");
    }

    String expirationDate = null;
    if (status == GRANTED) {

      expirationDate = creditRequestJson.getString("expiration_date");

      if (expirationDate == null || expirationDate.isEmpty()) {
        throw new DxBadRequestException("Expiration date is required");
      }

      try {
        LocalDateTime.parse(expirationDate, FORMATTER);
      } catch (Exception e) {
        throw new DxBadRequestException("Invalid expiration date format. Expected format: " + FORMATTER);
      }

      if (parseDateTime(expirationDate).isBefore(java.time.LocalDateTime.now())) {
        throw new DxBadRequestException("Expiration date must be in the future");
      }

    }

    Double amount = null;
    amount = creditRequestJson.getDouble("amount");

    creditService.updateCreditRequestStatus(requestId, status, transactedBy, amount, expirationDate)
      .onSuccess(transaction -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "PUT", "Credit Request Status Updated");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, transaction, this.urnGenerator);
        emailComposer.sendUserEmailForCreditApproval(requestId, status);
      })
      .onFailure(ctx::fail);

  }


  public void deductCredits(RoutingContext ctx) {


    User dxUser = ctx.user();
    JsonObject userJson = dxUser.principal();

    AccessValidator.validate(
      userJson,
      List.of( // primary roles (no scope check)
        DxRole.COS_ADMIN.getRole()),
      List.of(DxScope.CREDIT_MANAGEMENT.getScope(),DxScope.COS_ADMIN_ACCESS.getScope())
    );

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
        ResponseBuilder.sendSuccess(ctx, res, this.urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void addCredits(RoutingContext ctx) {

    User dxUser = ctx.user();
    JsonObject userJson = dxUser.principal();

    AccessValidator.validate(
      userJson,
      List.of( // primary roles (no scope check)
        DxRole.COS_ADMIN.getRole()),
      List.of(DxScope.CREDIT_MANAGEMENT.getScope(),DxScope.COS_ADMIN_ACCESS.getScope())
    );

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
        ResponseBuilder.sendSuccess(ctx, res, this.urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void createComputeRoleRequest(RoutingContext ctx) {

    User user = ctx.user();
    String userID = user.subject();
    String userName = user.principal().getString("name");

    if (ctx.body() == null || ctx.body().isEmpty() || ctx.body().asJsonObject() == null) {
      ctx.fail(new DxBadRequestException("Request Body is required and must be valid JSON."));
      return;
    }

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
              LOGGER.info("Requests in compute : {}",requests.toJson());
              ResponseBuilder.sendSuccess(ctx, requests, this.urnGenerator);
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
        ResponseBuilder.sendSuccess(ctx, "Compute Role Request created successfully", this.urnGenerator);
      })
      .onFailure(ctx::fail);

  }

  public void getAllComputeRequests(RoutingContext ctx) {


    User dxUser = ctx.user();
    JsonObject userJson = dxUser.principal();

    AccessValidator.validate(
      userJson,
      List.of( // primary roles (no scope check)
        DxRole.COS_ADMIN.getRole()),
      List.of(DxScope.COMPUTE_MANAGEMENT.getScope(),DxScope.COS_ADMIN_ACCESS.getScope())
    );

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
        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "GET",
          "Get Compute Role Requests"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue(), this.urnGenerator);
      }).onFailure(ctx::fail);

  }

  public void updateComputeRoleStatus(RoutingContext ctx) {


    User dxUser = ctx.user();
    JsonObject userJson = dxUser.principal();

    AccessValidator.validate(
      userJson,
      List.of( // primary roles (no scope check)
        DxRole.COS_ADMIN.getRole()),
      List.of(DxScope.COMPUTE_MANAGEMENT.getScope(),DxScope.COS_ADMIN_ACCESS.getScope())
    );

    JsonObject creditRequestJson = ctx.body().asJsonObject();

    User user = ctx.user();
    UUID approvedBy = UUID.fromString(user.subject());
    Status status = Status.fromString(creditRequestJson.getString("status"));
    UUID requestId = RequestHelper.getPathParamAsUUID(ctx, "id");

    creditService.updateComputeRoleStatus(requestId, status, approvedBy)
      .onSuccess(updated -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "PUT", "Compute Role Status Updated");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
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

  public void getUserCreditRequests(RoutingContext ctx) {
    AuditLog auditLog = AuditingHelper.createAuditLog(
      ctx.user(),
      RoutingContextHelper.getRequestPath(ctx),
      "GET",
      "Get Pending Credit Requests"
    );

    UUID userId = UUID.fromString(ctx.user().subject());

    creditService.getCreditRequestsByUserId(userId)
      .onSuccess(creditRequests -> {
        if (creditRequests == null || creditRequests.isEmpty()) {
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, new ArrayList<>(), this.urnGenerator);
          return;
        }

        List<Future> futures = creditRequests.stream()
          .map(cr -> {
            UUID crUserId = cr.userId();
            Promise<JsonObject> promise = Promise.promise();

            Future<JsonObject> balanceFuture = creditService.getBalance(crUserId)
              .map(res -> new JsonObject().put("balance", res.getString("balance")))
              .otherwise(new JsonObject().put("balance", 0.0));

            Future<JsonObject> expiryFuture = creditService.getExpirationDateByUserId(crUserId)
              .map(res -> new JsonObject().put("expirationDate", res.expirationDate().toString()))
              .otherwise(new JsonObject().put("expirationDate", (String) null));

            CompositeFuture.all(balanceFuture, expiryFuture)
              .onSuccess(cf -> {
                JsonObject creditRequestJson = JsonObject.mapFrom(cr)
                  .mergeIn(cf.resultAt(0))
                  .mergeIn(cf.resultAt(1));
                promise.complete(creditRequestJson);
              })
              .onFailure(err -> {
                LOGGER.warn("Failed to enrich credit request for user {}: {}", crUserId, err.getMessage());
                JsonObject creditRequestJson = JsonObject.mapFrom(cr)
                  .put("balance", 0.0)
                  .put("expirationDate", (String) null);
                promise.complete(creditRequestJson);
              });

            return promise.future();
          })
          .collect(Collectors.toList());

        CompositeFuture.all(futures)
          .onSuccess(cf -> {
            List<JsonObject> enrichedList = cf.list();
            RoutingContextHelper.setAuditingLog(ctx, auditLog);
            ResponseBuilder.sendSuccess(ctx, enrichedList, this.urnGenerator);
          })
          .onFailure(ctx::fail);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to fetch credit requests for user {}: {}", userId, err.getMessage());
        ctx.fail(err);
      });
  }


  public void deletePendingCreditRequest(RoutingContext ctx) {
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    String requestIdStr = ctx.pathParam("id");
    UUID requestId = UUID.fromString(requestIdStr);

    creditService.getCreditRequestById(requestId).compose(request -> {
        if (request == null) {
          return Future.failedFuture(new DxNotFoundException("Credit request not found"));
        }

        if (!request.userId().equals(userId)) {
          return Future.failedFuture(new DxForbiddenException("User is not authorized to delete this credit request"));
        }

        if (!request.status().equals(Status.PENDING.getStatus())) {
          return Future.failedFuture(new DxBadRequestException("Only pending credit requests can be deleted"));
        }

        return creditService.deletePendingCreditRequestById(requestId);
      })
      .onSuccess(deleted -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "DELETE",
          "Deleted Pending Credit Request"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        ResponseBuilder.sendSuccess(ctx, "Pending Credit Request deleted successfully", urnGenerator);
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
        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        if (enriched == null) {
          ResponseBuilder.sendSuccess(ctx, new JsonObject(), this.urnGenerator);
        } else {
          ResponseBuilder.sendSuccess(ctx, enriched, this.urnGenerator);
        }
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
        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "DELETE",
          "Deleted Pending Compute Request"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        ResponseBuilder.sendSuccess(ctx, "Pending Compute Request deleted successfully", urnGenerator);
      })
      .onFailure(ctx::fail);
  }

}
