package org.cdpg.dx.aaa.user.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.user.handler.UserHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.ScopeRule;
import org.cdpg.dx.auth.model.Scopes;

public class UserController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(UserController.class);
  private final UserHandler userHandler;

  public UserController(
      UserHandler userHandler) {
    this.userHandler = userHandler;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    var selfAccess = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);
    var customRoleAdminAccess =
        AuthorizationHandler.forScopesWithContext(
            ScopeRule.platform(Scopes.ROLE_MANAGEMENT), ScopeRule.org(Scopes.ORG_USER_MANAGEMENT));

    routerBuilder
        .operation("post-auth-v2-user-info")
        .handler(selfAccess)
        .handler(userHandler::addUserInfo);

    routerBuilder
        .operation("get-auth-v2-user-info")
        .handler(selfAccess)
        .handler(userHandler::getUserInfo);

    routerBuilder
        .operation("patch-auth-v2-user-info")
        .handler(selfAccess)
        .handler(userHandler::patchUserInfo);

    routerBuilder
        .operation("post-auth-v2-custom-role")
        .handler(customRoleAdminAccess)
        .handler(userHandler::addCustomRoleAndScopes);

    routerBuilder
        .operation("get-auth-v2-custom-role")
        .handler(selfAccess)
        .handler(userHandler::getAllCustomRoles);

    routerBuilder
        .operation("get-auth-v2-custom-role-requester")
        .handler(customRoleAdminAccess)
        .handler(userHandler::getAllCustomRolesByRequester);

    routerBuilder
        .operation("delete-auth-v2-custom-role")
        .handler(customRoleAdminAccess)
        .handler(userHandler::deleteCustomRoleScope);
  }
}
