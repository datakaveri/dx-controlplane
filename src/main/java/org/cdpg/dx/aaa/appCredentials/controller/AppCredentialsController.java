package org.cdpg.dx.aaa.appCredentials.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.apiserver.OperationIds;
import org.cdpg.dx.aaa.appCredentials.handler.AppCredentialsHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.model.Scopes;

public class AppCredentialsController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(AppCredentialsController.class);
  final AppCredentialsHandler appCredentialsHandler;

  public AppCredentialsController(AppCredentialsHandler appCredentialsHandler) {
    this.appCredentialsHandler = appCredentialsHandler;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering AppId APIs");

    var appAccess = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);

    builder
        .operation(OperationIds.OP_POST_APPID)
        .handler(appAccess)
        .handler(appCredentialsHandler::createApp);

    builder
      .operation(OperationIds.OP_POST_APPID_DX_USER)
      .handler(appAccess)
      .handler(appCredentialsHandler::postDxUserInfo);

    builder
        .operation(OperationIds.OP_GET_APPID)
        .handler(appAccess)
        .handler(appCredentialsHandler::getApp);
    builder
        .operation(OperationIds.OP_DELETE_APPID)
        .handler(appAccess)
        .handler(appCredentialsHandler::deleteApp);

    builder
        .operation(OperationIds.OP_UPDATE_STATUS_APPID)
        .handler(appAccess)
        .handler(appCredentialsHandler::changeAppStatus);
  }
}
