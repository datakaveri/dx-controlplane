package org.cdpg.dx.aaa.admin.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.admin.handler.AdminHandler;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.model.Scopes;

public class AdminController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(AdminController.class);
  private final AdminHandler adminHandler;

  public AdminController(AdminHandler adminHandler) {
    this.adminHandler = adminHandler;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    var selfAccess = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);
    var adminAccess = AuthorizationHandler.forScopes(Scopes.USER_MANAGEMENT);

    routerBuilder
        .operation("get-auth-v2-user")
        .handler(selfAccess)
        .handler(adminHandler::getDxUserInfo);

    routerBuilder
        .operation("get-auth-v2-user-id-admin")
        .handler(adminAccess)
        .handler(adminHandler::getDxUserFromKeycloak);

    routerBuilder
        .operation("get-auth-v2-admin-user")
        .handler(adminAccess)
        .handler(adminHandler::getAllDxUsersKeycloak);

    routerBuilder
        .operation("get-auth-v2-user-search")
        .handler(selfAccess)
        .handler(adminHandler::getAllUsersInfoKeycloak);

    routerBuilder
        .operation("put-auth-v2-user")
        .handler(selfAccess)
        .handler(adminHandler::updateDxUserInfo);
    routerBuilder
        .operation("put-auth-v2-user-password")
        .handler(selfAccess)
        .handler(adminHandler::updatePassword);

    routerBuilder
        .operation("post-auth-v2-user-update")
        .handler(selfAccess)
        .handler(adminHandler::updateUserStatus);

    routerBuilder
        .operation("delete-auth-v2-user")
        .handler(selfAccess)
        .handler(adminHandler::deleteDxUser);

    routerBuilder
        .operation("post-auth-v2-admin-id-update")
        .handler(adminAccess)
        .handler(adminHandler::updateDxUserStatusById);
  }
}
