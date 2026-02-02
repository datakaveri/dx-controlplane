package org.cdpg.dx.aaa.user.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.user.models.UserInfo;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;

import java.util.*;

import static org.cdpg.dx.aaa.organization.config.Constants.USER_ID;

public class UserHandler {

  private static final Logger LOGGER = LogManager.getLogger(UserHandler.class);
  private final UserService userService;
  private final URNGenerator urnGenerator;

  public UserHandler( UserService userService, URNGenerator urnGenerator) {
    this.userService = userService;
    this.urnGenerator = urnGenerator;
  }

  public void addCustomRoleAndScopes(RoutingContext ctx)
  {
     UUID userId = UUID.fromString(ctx.user().subject());

     JsonObject body = ctx.body().asJsonObject();

     body.put(USER_ID,userId);

    userService.addCustomRoleAndScope(body)
      .onSuccess(
        res -> {
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Add Custom Role and Scope");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "Success:Addtion of Custom Role and Scoep", this.urnGenerator);
        })
      .onFailure(ctx::fail);

  }


  public void addUserInfo(RoutingContext ctx) {
    LOGGER.info("Adding Additonal User Info");

    JsonObject body = ctx.body().asJsonObject();
    String userId = ctx.user().subject();
    body.put("userId", userId);
    UserInfo userInfo = UserInfo.fromJson(body);

    userService.createUserInfo(userInfo)
      .onSuccess(
        res -> {
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Credit Request Created");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "Success:Addtion of User Info", this.urnGenerator);
        })
      .onFailure(ctx::fail);

  }

  public void getUserInfo(RoutingContext ctx) {
    LOGGER.info("Getting Additonal User Info");
    String userId = ctx.user().subject();

    userService.getUserInfo(userId)
      .onSuccess(userInfo -> {
        if (userInfo == null) {
          ctx.fail(new DxNotFoundException("User info not found"));
          return;
        }
        ResponseBuilder.sendSuccess(ctx, userInfo.toJson(), null, this.urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void patchUserInfo(RoutingContext ctx) {
    LOGGER.info("Patching Additional User Info");

    String userId = ctx.user().subject();
    JsonObject updates = ctx.body().asJsonObject();

    userService.patchUserInfo(userId, updates)
      .onSuccess(res -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "PATCH",
          "User Info Updated");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, "Success: User Info Updated", this.urnGenerator);
      })
      .onFailure(ctx::fail);
  }





}
