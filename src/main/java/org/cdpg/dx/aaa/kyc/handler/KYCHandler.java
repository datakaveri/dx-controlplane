package org.cdpg.dx.aaa.kyc.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.credit.models.CreditRequestAuditOperation;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.credit.util.CreditRequestAuditLogHelper;
import org.cdpg.dx.aaa.kyc.service.KYCService;
import org.cdpg.dx.aaa.kyc.util.KYCAuditLogHelper;
import org.cdpg.dx.aaa.kyc.util.KYCAuditOperation;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.UUID;

import static org.cdpg.dx.aaa.common.Constants.ID;


public class KYCHandler {
  private static final Logger LOGGER = LogManager.getLogger(KYCHandler.class);
  private final KYCService kycService;
  private final KeycloakUserService keycloakUserService;
  private final CreditService creditService;
  private final URNGenerator urnGenerator;


  public KYCHandler(KYCService kycService, KeycloakUserService keycloakService, CreditService creditService, URNGenerator urnGenerator) {
    this.kycService = kycService;
    this.keycloakUserService = keycloakService;
    this.creditService = creditService;
    this.urnGenerator = urnGenerator;
  }

  public void verifyKYC(RoutingContext ctx) {
    LOGGER.debug("verifyKYC: >>>>>>>>>>>>>>>>");
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    JsonObject OrgRequestJson = ctx.body().asJsonObject();

    LOGGER.debug("orgRequestJson: {}", OrgRequestJson);

    String code = OrgRequestJson.getString("auth_code");
    String codeVerifier = OrgRequestJson.getString("code_verifier");

    if (code == null || codeVerifier == null) {
      ctx.fail(new DxBadRequestException("Required parameter missing"));
      return;
    }
    kycService.getKYCData(userId, code, codeVerifier)
      .onSuccess(res -> {
//        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
//          RoutingContextHelper.getRequestPath(ctx), "POST", "Verify KYC Data");
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        UserActivityAuditLogBuilder auditLogBuilder =
          KYCAuditLogHelper.buildAudit(
            ctx, KYCAuditOperation.VERIFY);

        RoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
        ResponseBuilder.sendSuccess(ctx, res,urnGenerator);
      })
      .onFailure(ctx::fail);


  }

  public void confirmKYC(RoutingContext ctx) {
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());
    String codeVerifier = ctx.pathParam("id");

    if (codeVerifier == null) {
      ctx.fail(new DxBadRequestException("Missing required parameters"));
      return;
    }

    kycService.confirmKYCData(userId, codeVerifier, user.principal().getString("name"))
      .onSuccess(res -> {
//        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
//          RoutingContextHelper.getRequestPath(ctx), "GET", "Confirm KYC Data");
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        UserActivityAuditLogBuilder auditLogBuilder =
          KYCAuditLogHelper.buildAudit(
            ctx, KYCAuditOperation.CONFIRM);
        RoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
        ResponseBuilder.sendSuccess(ctx, res,urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void revokeKYC(RoutingContext ctx) {
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    keycloakUserService.setKycVerifiedFalse(userId)
      .compose(kyc ->
        keycloakUserService.removeRoleFromUser(userId, DxRole.COMPUTE)
          .compose(v -> creditService.getComputeRoleRequestByUserId(userId))
          .compose(computeRoleRequest -> {
            if (computeRoleRequest != null) {
              return creditService.updateComputeRoleStatus(
                computeRoleRequest.id(), Status.REJECTED, computeRoleRequest.approvedBy());
            } else {
              return Future.succeededFuture();
            }
          })
          .recover(err -> {
            // Log and ignore any failure from getComputeRoleRequestByUserId or update
            //
            LOGGER.warn("Failed to process compute role revocation: {}", err.getMessage());
            return Future.succeededFuture();
          })
      )
      .onSuccess(res -> {
//        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
//          RoutingContextHelper.getRequestPath(ctx), "POST", "KYC revoked");
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        UserActivityAuditLogBuilder auditLogBuilder =
          KYCAuditLogHelper.buildAudit(
            ctx, KYCAuditOperation.REVOKE);
        RoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
        ResponseBuilder.sendSuccess(ctx, "KYC revoked successfully",urnGenerator);
      })
      .onFailure(ctx::fail);
  }


}
