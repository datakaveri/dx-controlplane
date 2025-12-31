package org.cdpg.dx.aaa.appCredentials.controller;


import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.apiserver.OperationIds;
import org.cdpg.dx.aaa.appCredentials.handler.AppCredentialsHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;

public class AppCredentialsController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(AppCredentialsController.class);
  final AppCredentialsHandler appCredentialsHandler;

  public AppCredentialsController(AppCredentialsHandler appCredentialsHandler) {
    this.appCredentialsHandler = appCredentialsHandler;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering AppId APIs");
    Handler<RoutingContext> authorizationHandler =
        AuthorizationHandler.forRoles(DxRole.CONSUMER);

    builder
        .operation(OperationIds.OP_POST_APPID)
        .handler(authorizationHandler)
        .handler(appCredentialsHandler::createApp);

    builder
        .operation(OperationIds.OP_GET_APPID)
        .handler(authorizationHandler)
        .handler(appCredentialsHandler::getApp);
    builder
        .operation(OperationIds.OP_DELETE_APPID)
        .handler(authorizationHandler)
        .handler(appCredentialsHandler::deleteApp);

  }
}
