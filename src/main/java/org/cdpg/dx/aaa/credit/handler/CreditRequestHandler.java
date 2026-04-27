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
import org.cdpg.dx.aaa.credit.util.CreditRequestEnricher;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.time.LocalDateTime;
import java.util.*;

import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.credit.util.Constants.*;
import static org.cdpg.dx.aaa.credit.models.Status.GRANTED;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class CreditRequestHandler {

  private static final Logger LOGGER = LogManager.getLogger(CreditRequestHandler.class);
  private final CreditService creditService;
  private final EmailComposer emailComposer;
  private final KeycloakUserService keycloakUserService;
  private final URNGenerator urnGenerator;

  public CreditRequestHandler(CreditService creditService, EmailComposer emailComposer, KeycloakUserService keycloakUserService, URNGenerator urnGenerator) {
    this.creditService = creditService;
    this.emailComposer = emailComposer;
    this.keycloakUserService = keycloakUserService;
    this.urnGenerator = urnGenerator;
  }


  public void createCreditRequest(RoutingContext ctx) {

    JsonObject creditRequestJson = Optional.ofNullable(ctx.body().asJsonObject())
      .orElse(new JsonObject());

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    keycloakUserService.getUserById(userId)
      .compose(keycloakUser -> {

        // enrich request JSON
        creditRequestJson.put("user_id", keycloakUser.sub().toString());

        creditRequestJson.put("user_name", keycloakUser.name());

        CreditRequest creditRequest = CreditRequest.fromJson(creditRequestJson);

        return creditService.createCreditRequest(creditRequest);
      })
      .onSuccess(requests -> {

        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx, requests.toJson(), CreditRequestAuditOperation.REQUEST_CREDITS);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
        ResponseBuilder.sendSuccess(ctx, requests, this.urnGenerator);

        emailComposer.sendEmailForCreditRequest(user);
      })
      .onFailure(ctx::fail);
  }


  public void getCreditRequests(RoutingContext ctx) {
    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST)
      .apiToDbMap(ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST)
      .allowedTimeFields(Set.of(CREATED_AT))
      .defaultTimeField(REQUESTED_AT)
      .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
      .allowedSortFields(ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST.keySet())
      .build();

    creditService.getAllCreditRequests(request)
      .onSuccess(result -> {
        List<CreditRequest> creditRequests = result.data();

        CreditRequestEnricher.enrichWithBalanceAndExpiry(creditRequests, creditService)
          .onSuccess(enrichedList -> {
            UserActivityAuditLogBuilder auditLogBuilder =
              CreditRequestAuditLogHelper.buildAudit(
                ctx, new JsonObject(), CreditRequestAuditOperation.GET_CREDIT_REQUESTS);
            CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
            ResponseBuilder.sendSuccess(ctx, enrichedList, result.paginationInfo(), this.urnGenerator);
          })
          .onFailure(ctx::fail);
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

        CreditRequestEnricher.enrichWithBalanceAndExpiry(creditRequests, creditService)
          .onSuccess(enrichedList -> {
            UserActivityAuditLogBuilder auditLogBuilder =
              CreditRequestAuditLogHelper.buildAudit(
                ctx, new JsonObject(), CreditRequestAuditOperation.GET_CREDIT_REQUESTS);
            CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
            ResponseBuilder.sendSuccess(ctx, enrichedList, this.urnGenerator);
          })
          .onFailure(ctx::fail);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to fetch credit requests for user {}: {}", userId, err.getMessage());
        ctx.fail(err);
      });
  }


  public void updateCreditRequestStatus(RoutingContext ctx) {
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
//        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
//          RoutingContextHelper.getRequestPath(ctx), "PUT", "Credit Request Status Updated");
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx, transaction.toJson(), CreditRequestAuditOperation.UPDATE);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(ctx, transaction, this.urnGenerator);
        emailComposer.sendUserEmailForCreditApproval(requestId, status);
      })
      .onFailure(ctx::fail);

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
//        AuditLog auditLog = AuditingHelper.createAuditLog(
//          ctx.user(),
//          RoutingContextHelper.getRequestPath(ctx),
//          "DELETE",
//          "Deleted Pending Credit Request"
//        );
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx,new JsonObject().put(ID,requestIdStr), CreditRequestAuditOperation.DELETE);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(ctx, "Pending Credit Request deleted successfully", urnGenerator);
      })
      .onFailure(ctx::fail);
  }

}
