package org.cdpg.dx.aaa.credit.Controller;

import static org.cdpg.dx.auth.authorization.handler.AuthorizationHandler.kycVerification;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.credit.handler.ComputeRoleHandler;
import org.cdpg.dx.aaa.credit.handler.CreditBalanceHandler;
import org.cdpg.dx.aaa.credit.handler.CreditRequestHandler;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.model.Scopes;

public class CreditController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(CreditController.class);
  private final CreditRequestHandler creditRequestHandler;
  private final CreditBalanceHandler creditBalanceHandler;
  private final ComputeRoleHandler computeRoleHandler;
  private final Boolean isKycRequired;
  private final AuditingHandler auditingHandler;

  public CreditController(
      CreditRequestHandler creditRequestHandler,
      CreditBalanceHandler creditBalanceHandler,
      ComputeRoleHandler computeRoleHandler,
      AuditingHandler auditingHandler,
      Boolean isKycRequired) {
    this.creditRequestHandler = creditRequestHandler;
    this.creditBalanceHandler = creditBalanceHandler;
    this.computeRoleHandler = computeRoleHandler;
    this.isKycRequired = isKycRequired;
    this.auditingHandler = auditingHandler;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    var selfAccess = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);
    var adminAccess = AuthorizationHandler.forScopes(Scopes.USER_MANAGEMENT);
    var computeAccess = AuthorizationHandler.forScopes(Scopes.COMPUTE_MANAGEMENT);

    routerBuilder
      .operation("post-auth-v2-credit-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(computeAccess)
      .handler(kycVerification(isKycRequired))
      .handler(creditRequestHandler::createCreditRequest);

    routerBuilder
      .operation("get-auth-v2-credit")
      .handler(auditingHandler::handleApiAudit)
      .handler(adminAccess)
      .handler(creditRequestHandler::getCreditRequests);

    routerBuilder
      .operation("get-auth-v2-user-credit")
      .handler(auditingHandler::handleApiAudit)
      .handler(selfAccess)
      .handler(creditRequestHandler::getUserCreditRequests);

    routerBuilder
      .operation("delete-auth-v2-user-credit")
      .handler(auditingHandler::handleApiAudit)
      .handler(selfAccess)
      .handler(creditRequestHandler::deletePendingCreditRequest);


    routerBuilder
      .operation("put-auth-v2-credit-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(adminAccess)
      .handler(creditRequestHandler::updateCreditRequestStatus);

    routerBuilder
      .operation("put-auth-v2-user-credit")
      .handler(auditingHandler::handleApiAudit)
      .handler(adminAccess)
      .handler(creditBalanceHandler::deductCredits);

    routerBuilder
      .operation("put-auth-v2-user-credit-add")
      .handler(auditingHandler::handleApiAudit)
      .handler(adminAccess)
      .handler(creditBalanceHandler::addCredits);

    routerBuilder
      .operation("post-auth-v2-compute-role-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(selfAccess)
      .handler(kycVerification(isKycRequired))
      .handler(computeRoleHandler::createComputeRoleRequest);


    routerBuilder
      .operation("get-auth-v2-compute-role-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(adminAccess)
      .handler(computeRoleHandler::getAllComputeRequests);

    routerBuilder
      .operation("get-auth-v2-user-compute-role-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(selfAccess)
      .handler(computeRoleHandler::getComputeRequests);

    routerBuilder
      .operation("delete-auth-v2-user-compute-role-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(selfAccess)
      .handler(computeRoleHandler::deletePendingComputeRequests);

    routerBuilder
      .operation("put-auth-v2-compute-role-request")
      .handler(auditingHandler::handleApiAudit)
      .handler(adminAccess)
      .handler(computeRoleHandler::updateComputeRoleStatus);

    routerBuilder
      .operation("get-auth-v2-admin-user-credit-balance")
      .handler(auditingHandler::handleApiAudit)
      .handler(adminAccess)
      .handler(creditBalanceHandler::getBalanceofUser);

    routerBuilder
      .operation("get-auth-v2-user-credit-balance")
      .handler(auditingHandler::handleApiAudit)
      .handler(computeAccess)
      .handler(kycVerification(isKycRequired))
      .handler(creditBalanceHandler::getBalance);
  }
}