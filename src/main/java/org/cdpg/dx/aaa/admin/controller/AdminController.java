package org.cdpg.dx.aaa.admin.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.admin.handler.AdminHandler;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.organization.handler.OrganizationHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;

public class AdminController implements ApiController {
    private static final Logger LOGGER = LogManager.getLogger(AdminController.class);
    private final AdminHandler adminHandler;

    public AdminController(AdminHandler adminHandler) {
        this.adminHandler = adminHandler;
    }
    @Override
    public void register(RouterBuilder routerBuilder) {

        routerBuilder
                .operation("get-auth-v1-user")
                .handler(adminHandler::getDxUserInfo);

        routerBuilder
                .operation("get-auth-v1-user-id-admin")
                .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN))
                .handler(adminHandler::getDxUserFromKeycloak);

        routerBuilder
                .operation("get-auth-v1-admin-user")
                .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN))
                .handler(adminHandler::getAllDxUsersKeycloak);

        routerBuilder
                .operation("put-auth-v1-user")
                .handler(adminHandler::updateDxUserInfo);

        routerBuilder
                .operation("put-auth-v1-user-password")
                .handler(adminHandler::updatePassword);

        routerBuilder
                .operation("post-auth-v1-user-deactivate")
                .handler(adminHandler::deactivateDxUser);

      routerBuilder
        .operation("delete-auth-v1-user-id")
        .handler(adminHandler::deleteDxUser);

    }
}
