package org.cdpg.dx.aaa.credit.Controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.credit.handler.ComputeRoleHandler;
import org.cdpg.dx.aaa.credit.handler.CreditBalanceHandler;
import org.cdpg.dx.aaa.credit.handler.CreditRequestHandler;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;

public class CreditController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(CreditController.class);
  private final CreditRequestHandler creditRequestHandler;
  private final CreditBalanceHandler creditBalanceHandler;
  private final ComputeRoleHandler computeRoleHandler;
  private final Boolean isKycRequired;
  private final AuditingHandler auditingHandler;

  public CreditController(CreditRequestHandler creditRequestHandler, CreditBalanceHandler creditBalanceHandler, ComputeRoleHandler computeRoleHandler, AuditingHandler auditingHandler, Boolean isKycRequired) {
    this.creditRequestHandler = creditRequestHandler;
    this.creditBalanceHandler = creditBalanceHandler;
    this.computeRoleHandler = computeRoleHandler;
    this.isKycRequired = isKycRequired;
    this.auditingHandler = auditingHandler;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    routerBuilder
      .operation("post-auth-v2-credit-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COMPUTE))
      .handler(creditRequestHandler::createCreditRequest);

    routerBuilder
      .operation("get-auth-v2-credit")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditRequestHandler::getCreditRequests);

    routerBuilder
      .operation("get-auth-v2-user-credit")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(creditRequestHandler::getUserCreditRequests);

    routerBuilder
      .operation("delete-auth-v2-user-credit")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(creditRequestHandler::deletePendingCreditRequest);


    routerBuilder
      .operation("put-auth-v2-credit-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditRequestHandler::updateCreditRequestStatus);

    routerBuilder
      .operation("put-auth-v2-user-credit")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditBalanceHandler::deductCredits);

    routerBuilder
      .operation("put-auth-v2-user-credit-add")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditBalanceHandler::addCredits);

    routerBuilder
      .operation("post-auth-v2-compute-role-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.KycVerification(isKycRequired))
      .handler(computeRoleHandler::createComputeRoleRequest);


    routerBuilder
      .operation("get-auth-v2-compute-role-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(computeRoleHandler::getAllComputeRequests);

    routerBuilder
      .operation("get-auth-v2-user-compute-role-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(computeRoleHandler::getComputeRequests);

    routerBuilder
      .operation("delete-auth-v2-user-compute-role-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(computeRoleHandler::deletePendingComputeRequests);

    routerBuilder
      .operation("put-auth-v2-compute-role-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(computeRoleHandler::updateComputeRoleStatus);

    routerBuilder
      .operation("get-auth-v2-admin-user-credit-balance")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditBalanceHandler::getBalanceofUser);

    routerBuilder
      .operation("get-auth-v2-user-credit-balance")
      .handler(auditingHandler::handleApiAudit)
      .handler(AuthorizationHandler.forRoles(DxRole.COMPUTE))
      .handler(AuthorizationHandler.KycVerification(isKycRequired))
      .handler(creditBalanceHandler::getBalance);
  }
}
