package org.cdpg.dx.aaa.credit.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.models.*;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.credit.util.CreditRequestAuditLogHelper;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.*;

public class CreditBalanceHandler {

  private static final Logger LOGGER = LogManager.getLogger(CreditBalanceHandler.class);
  private final CreditService creditService;
  private final EmailComposer emailComposer;
  private final KeycloakUserService keycloakUserService;
  private final URNGenerator urnGenerator;

  public CreditBalanceHandler(CreditService creditService, EmailComposer emailComposer, KeycloakUserService keycloakUserService, URNGenerator urnGenerator) {
    this.creditService = creditService;
    this.emailComposer = emailComposer;
    this.keycloakUserService = keycloakUserService;
    this.urnGenerator = urnGenerator;
  }


  public void getBalance(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    creditService.getBalance(userId)
      .onSuccess(balance -> {
        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx, balance, CreditRequestAuditOperation.GET_BALANCE);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(ctx, balance, this.urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void getBalanceofUser(RoutingContext ctx) {
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "id");
    creditService.getBalance(userId)
      .onSuccess(res -> {

        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx, res, CreditRequestAuditOperation.GET_BALANCE);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
        ResponseBuilder.sendSuccess(ctx,res, this.urnGenerator);
      })
      .onFailure(ctx::fail);
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
//        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
//          RoutingContextHelper.getRequestPath(ctx), "PUT", "Credits Deducted");
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx, res.toJson(), CreditRequestAuditOperation.DEBIT);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(ctx, res, this.urnGenerator);
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
//        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
//          RoutingContextHelper.getRequestPath(ctx), "PUT", "Credits Added");
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        UserActivityAuditLogBuilder auditLogBuilder =
          CreditRequestAuditLogHelper.buildAudit(
            ctx, res.toJson(), CreditRequestAuditOperation.CREDIT);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(ctx, res, this.urnGenerator);
      })
      .onFailure(ctx::fail);
  }

}
