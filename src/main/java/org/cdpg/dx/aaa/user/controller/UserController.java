package org.cdpg.dx.aaa.user.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.user.handler.UserHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;

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

  }

}
