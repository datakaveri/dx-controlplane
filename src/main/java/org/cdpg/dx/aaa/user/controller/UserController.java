package org.cdpg.dx.aaa.user.controller;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.user.handler.UserHandler;
import org.cdpg.dx.auth.v2.handler.AuthenticationHandler;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.handler.ScopeRule;
import org.cdpg.dx.auth.v2.model.Scopes;

public class UserController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(UserController.class);
  private final UserHandler userHandler;
  private final AuthenticationHandler authenticationV2;
  private final AuthorizationHandler authorizationV2;

  public UserController(
      UserHandler userHandler,
      AuthenticationHandler authenticationV2,
      AuthorizationHandler authorizationV2) {
    this.userHandler = userHandler;
    this.authenticationV2 = authenticationV2;
    this.authorizationV2 = authorizationV2;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    var selfAccess = authorizationV2.forScopes(Scopes.DATA_ACCESS);
    var customRoleAdminAccess =
        authorizationV2.forScopesWithContext(
            ScopeRule.platform(Scopes.ROLE_MANAGEMENT),
            ScopeRule.org(Scopes.ORG_USER_MANAGEMENT));

    routerBuilder
      .operation("post-auth-v2-user-info")
      .handler(authenticationV2)
      .handler(selfAccess)
      .handler(userHandler::addUserInfo);

    routerBuilder
      .operation("get-auth-v2-user-info")
      .handler(authenticationV2)
      .handler(selfAccess)
      .handler(userHandler::getUserInfo);

    routerBuilder
      .operation("patch-auth-v2-user-info")
      .handler(authenticationV2)
      .handler(selfAccess)
      .handler(userHandler::patchUserInfo);

    routerBuilder
      .operation("post-auth-v2-custom-role")
      .handler(authenticationV2)
      .handler(customRoleAdminAccess)
      .handler(userHandler::addCustomRoleAndScopes);

    routerBuilder
      .operation("get-auth-v2-custom-role")
      .handler(authenticationV2)
      .handler(selfAccess)
      .handler(userHandler::getAllCustomRoles);

    routerBuilder
      .operation("get-auth-v2-custom-role-requester")
      .handler(authenticationV2)
      .handler(customRoleAdminAccess)
      .handler(userHandler::getAllCustomRolesByRequester);

    routerBuilder
      .operation("delete-auth-v2-custom-role")
      .handler(authenticationV2)
      .handler(customRoleAdminAccess)
      .handler(userHandler::deleteCustomRoleScope);
  }

}