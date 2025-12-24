package org.cdpg.dx.aaa.credit.Controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.credit.handler.CreditHandler;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.checkerframework.checker.units.qual.A;

public class CreditController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(CreditController.class);
  private final CreditHandler creditHandler;

  public CreditController(CreditHandler creditHandler) {
    this.creditHandler = creditHandler;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    routerBuilder
      .operation("post-auth-v2-credit-request")
      .handler(AuthorizationHandler.forRoles(DxRole.COMPUTE))
      .handler(creditHandler::createCreditRequest);

    routerBuilder
      .operation("get-auth-v2-credit")
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditHandler::getCreditRequests);

    routerBuilder
      .operation("get-auth-v2-user-credit")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(creditHandler::getUserCreditRequests);

    routerBuilder
      .operation("delete-auth-v2-user-credit")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(creditHandler::deletePendingCreditRequest);


    routerBuilder
      .operation("put-auth-v2-credit-request")
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditHandler::updateCreditRequestStatus);

    routerBuilder
      .operation("put-auth-v2-user-credit")
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditHandler::deductCredits);

    routerBuilder
      .operation("put-auth-v2-user-credit-add")
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditHandler::addCredits);

    routerBuilder
      .operation("post-auth-v2-compute-role-request")
      .handler(AuthorizationHandler.requireKycVerified())
      .handler(creditHandler::createComputeRoleRequest);


    routerBuilder
      .operation("get-auth-v2-compute-role-request")
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditHandler::getAllComputeRequests);

    routerBuilder
      .operation("get-auth-v2-user-compute-role-request")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(creditHandler::getComputeRequests);

    routerBuilder
      .operation("delete-auth-v2-user-compute-role-request")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(creditHandler::deletePendingComputeRequests);

    routerBuilder
      .operation("put-auth-v2-compute-role-request")
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditHandler::updateComputeRoleStatus);

    routerBuilder
      .operation("get-auth-v2-admin-user-credit-balance")
      .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
      .handler(creditHandler::getBalanceofUser);

    routerBuilder
      .operation("get-auth-v2-user-credit-balance")
      .handler(AuthorizationHandler.forRoles(DxRole.COMPUTE))
      .handler(AuthorizationHandler.requireKycVerified())
      .handler(creditHandler::getBalance);
  }
}

