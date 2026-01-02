package org.cdpg.dx.aaa.delegation.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.delegation.handler.DelegationHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;

public class DelegationController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(DelegationController.class);
  private final DelegationHandler delegationHandler;

  public DelegationController(DelegationHandler delegationHandler)
  {
    this.delegationHandler = delegationHandler;
  }

  @Override
  public void register(RouterBuilder routerBuilder)
  {

    routerBuilder
      .operation("post-auth-v2-delegation")
    .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))  //anyone can create the delegation grant.
    .handler(delegationHandler::createDelegationGrant);

    routerBuilder
      .operation("delete-auth-v2-delegation-id")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))  //anyone can create the delegation grant.
      .handler(delegationHandler::deleteDelegationGrant);

    routerBuilder
      .operation("get-auth-v2-delegation-id")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))   //both delegate and delegator can view the requests
      .handler(delegationHandler::getDelegationGrant);

    routerBuilder
      .operation("get-auth-v2-delegation-delegate")
      .handler(AuthorizationHandler.forRoles(DxRole.DELEGATE))   //both delegate and delegator can view the requests
      .handler(delegationHandler::getAllDelegationsOfDelegate);

    routerBuilder
      .operation("get-auth-v2-delegation-delegator")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))   //both delegate and delegator can view the requests
      .handler(delegationHandler::getAllDelegationsByDelegator);

//    routerBuilder
//      .operation("get-auth-v2-user-delegation")
//      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))   //both delegate and delegator can view the requests
//      .handler(delegationHandler::getAllDelegationsByUser);
//
//
//    routerBuilder
//      .operation("delete-auth-v2-delegation")
//      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))   //both delegate and delegator can view the requests
//      .handler(delegationHandler::deleteDelegationGrant);


    routerBuilder
      .operation("post-auth-v2-delegation-request")
      .handler(AuthorizationHandler.forRoles(DxRole.DELEGATE))   //delegate
      .handler(delegationHandler::createUpdateDelegationRequest);

    routerBuilder
      .operation("get-auth-v2-delegation-request")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))  // delegator
      .handler(delegationHandler::getDelegationRequest);

    routerBuilder
      .operation("put-auth-v2-delegation-request")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))  //delegator
      .handler(delegationHandler::updateDelegationRequest);


  }

}
