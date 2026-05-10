package org.cdpg.dx.aaa.appCredentials.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.apiserver.OperationIds;
import org.cdpg.dx.aaa.appCredentials.handler.AppCredentialsHandler;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.model.Scopes;

public class AppCredentialsController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(AppCredentialsController.class);
  final AppCredentialsHandler appCredentialsHandler;
  private final AuthorizationHandler authorizationV2;

  public AppCredentialsController(
      AppCredentialsHandler appCredentialsHandler,
      AuthorizationHandler authorizationV2) {
    this.appCredentialsHandler = appCredentialsHandler;
    this.authorizationV2 = authorizationV2;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering AppId APIs");

    var appAccess = authorizationV2.forScopes(Scopes.DATA_ACCESS);

    builder
        .operation(OperationIds.OP_POST_APPID)
        .handler(appAccess)
        .handler(appCredentialsHandler::createApp);

    builder
      .operation(OperationIds.OP_POST_APPID_DX_USER)
      .handler(authenticationV2)
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
