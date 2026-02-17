package org.cdpg.dx.aaa.user.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.user.handler.UserHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;

import static org.cdpg.dx.auth.authorization.model.DxRole.*;

public class UserController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(UserController.class);
  private final UserHandler userHandler;

  public UserController(UserHandler userHandler) {
    this.userHandler = userHandler;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    routerBuilder
      .operation("post-auth-v2-user-info")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(userHandler::addUserInfo);

    routerBuilder
      .operation("get-auth-v2-user-info")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(userHandler::getUserInfo);

    routerBuilder
      .operation("patch-auth-v2-user-info")
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
      .handler(userHandler::patchUserInfo);

    routerBuilder
      .operation("post-auth-v2-custom-role")
      .handler(AuthorizationHandler.forRoles(ORG_ADMIN,COS_ADMIN))
      .handler(userHandler::addCustomRoleAndScopes);

    routerBuilder
      .operation("get-auth-v2-custom-role")
      .handler(AuthorizationHandler.forRoles(CONSUMER))
      .handler(userHandler::getAllCustomRoles);

    routerBuilder
      .operation("get-auth-v2-custom-role-requester")
      .handler(AuthorizationHandler.forRoles(ORG_ADMIN,COS_ADMIN))
      .handler(userHandler::getAllCustomRolesByRequester);

    routerBuilder
      .operation("delete-auth-v2-custom-role")
      .handler(AuthorizationHandler.forRoles(ORG_ADMIN,COS_ADMIN))
      .handler(userHandler::deleteCustomRoleScope);
  }

}
