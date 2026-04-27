package org.cdpg.dx.aaa.user.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.Build;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.user.models.UserInfo;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;

import java.util.*;

import static org.cdpg.dx.aaa.organization.config.Constants.USER_ID;
import static org.cdpg.dx.aaa.user.util.constants.*;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class UserHandler {

  private static final Logger LOGGER = LogManager.getLogger(UserHandler.class);
  private final UserService userService;
  private final URNGenerator urnGenerator;

  public UserHandler( UserService userService, URNGenerator urnGenerator) {
    this.userService = userService;
    this.urnGenerator = urnGenerator;
  }

  public void addCustomRoleAndScopes(RoutingContext ctx) {

    UUID requesterId = UUID.fromString(ctx.user().subject());
    JsonObject body = ctx.body().asJsonObject();

    UUID targetUserId = UUID.fromString(body.getString(USER_ID));
    JsonArray newScopes = body.getJsonArray(SCOPE, new JsonArray());

    body.put(REQUESTED_BY, requesterId.toString());

    userService
      .getCustomRoleByUserAndRequester(targetUserId, requesterId)
      .recover(err -> {
        // No existing entry → create new
        if (err instanceof DxNotFoundException) {
          return Future.succeededFuture(null);
        }
        return Future.failedFuture(err);
      })
      .compose(existingRole -> {

        // CASE 1: Existing role → merge scopes
        if (existingRole != null) {

          JsonArray existingScopes = existingRole.scope();
          Set<String> mergedScopes = new HashSet<>();

          if (existingScopes != null) {
            existingScopes.forEach(s -> mergedScopes.add((String) s));
          }
          newScopes.forEach(s -> mergedScopes.add((String) s));

          JsonArray updatedScopes = new JsonArray(new ArrayList<>(mergedScopes));

          // FIX: return CustomRole directly
          return userService.updateCustomScope(existingRole.id(), updatedScopes);
        }

        // CASE 2: No role exists → create new
        return userService.addCustomRoleAndScope(body);
      })
      .onSuccess(customRole -> {

        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "POST",
          "Add Custom Role and Scope"
        );

        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(
          ctx,
          customRole,
          this.urnGenerator
        );
      })
      .onFailure(ctx::fail);
  }

  public void deleteCustomRoleScope(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());
    JsonObject body = ctx.body().asJsonObject();

    userService
      .deleteScope(userId, body)
      .onSuccess(deleted -> {

        if (!deleted) {
          ctx.fail(
            new DxInternalServerErrorException("Failed to delete custom role scope")
          );
          return;
        }

        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "DELETE",
          "Delete Custom Role Scope"
        );

        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(
          ctx,
          "Scope deleted successfully",
          this.urnGenerator
        );
      })
      .onFailure(ctx::fail);
  }



  public void getAllCustomRoles(RoutingContext ctx) {

    User user = ctx.user();
    JsonObject userJson = user.principal();


    // Build the paginated request
    PaginatedRequest request =
      PaginationRequestBuilder.from(ctx)
        .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_CUSTOM_ROLE) // Map API filters to DB columns
        .apiToDbMap(API_TO_DB_CUSTOM_ROLE) // API field -> DB field
        .allowedTimeFields(Set.of(CREATED_AT))
        .defaultTimeField(CREATED_AT)
        .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
        .allowedSortFields(API_TO_DB_CUSTOM_ROLE.keySet())
        .build();

    // Call service
    userService
      .getAllCustomRoles(request)
      .onSuccess(res -> {
//        ActivityAuditLogBuilder auditLog =
//          CustomRoleAuditHelper.buildGetCustomRolesAudit(ctx);
//        RoutingContextHelper.setAuditingLogNew(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, res.data(), res.paginationInfo(), urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void getAllCustomRolesByRequester(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    userService
      .getCustomRoleRequestByRequester(userId)
      .onSuccess(customRoles -> {
        ResponseBuilder.sendSuccess(ctx, customRoles, urnGenerator);
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
