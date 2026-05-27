package org.cdpg.dx.aaa.appCredentials.handler;

import static org.cdpg.dx.aaa.appCredentials.util.Constants.*;
import static org.cdpg.dx.aaa.bookmarks.util.Constants.ALLOWED_FILTER_MAP_FOR_BOOKMARK_REQUEST;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.ORG_ID;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

import java.time.LocalDateTime;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.delegation.DelegationHandlerValidator;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;

public class AppCredentialsHandler {
  private static final Logger LOGGER = LogManager.getLogger(AppCredentialsHandler.class);
  private final AppCredentialsService appCredentialsService;
  private final DelegationHandlerValidator delegationHandlerValidator;
  private final URNGenerator urnGenerator;

  public AppCredentialsHandler(AppCredentialsService appCredentialsService, DelegationHandlerValidator delegationHandlerValidator, URNGenerator urnGenerator) {
    this.appCredentialsService = appCredentialsService;
    this.delegationHandlerValidator = new DelegationHandlerValidator();
    this.urnGenerator = urnGenerator;
  }

    public void createApp(RoutingContext ctx) {
    User user = ctx.user();
    if (user == null || user.subject() == null) {
      LOGGER.error("User or user subject is null in context");
      ctx.fail(new DxUnauthorizedException("User or user subject is null in context"));
      return;
    }

    UUID userId;
    String orgId;
    try {
      userId = UUID.fromString(user.subject());
      orgId = user.principal().getString("organisation_id");
    } catch (IllegalArgumentException e) {
      LOGGER.error("Invalid user subject for UUID: {}", user.subject(), e);
      ctx.fail(new DxBadRequestException("Invalid user subject for UUID"));
      return;
    }

      List<String> user_roles = extractRoles(user);
//      String role = getHighestRole(roles);
      JsonObject body = ctx.body().asJsonObject();

      JsonArray rolesArray = body.getJsonArray("roles");
      String role = "";
      if (rolesArray != null && !rolesArray.isEmpty()) {
        JsonObject firstRole = rolesArray.getJsonObject(0);
        if (firstRole != null) {
          role = firstRole.getString("role", "");
        }
      }

    body.put(USER_ID,userId);
    body.put(ROLE,role);
    body.put(ORG_ID,orgId);

      try {
        delegationHandlerValidator.validateCreateDelegationGrantBody(userId, user_roles ,body);
      } catch (DxBadRequestException | DxForbiddenException e) {
        ctx.fail(e);
        return;
      }


    appCredentialsService
      .createApp(body)
      .onSuccess(
        appCredentialRes -> ResponseBuilder.sendSuccess(ctx, appCredentialRes, urnGenerator))
      .onFailure(
        err -> {
          LOGGER.error("Failed to create client credentials: {}", err.getMessage(), err);
          ctx.fail(err);
        });
  }

  public void getApp(RoutingContext ctx) {
    LOGGER.trace("getApps() handler started");
    UUID userId = UUID.fromString(ctx.user().subject());

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .additionalFilters(Map.of("user_id", userId.toString()))
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_APP_CREDENTIALS)
            .apiToDbMap(ALLOWED_FILTER_MAP_FOR_APP_CREDENTIALS)
            .allowedTimeFields(Set.of("created_at"))
            .defaultTimeField("created_at")
            .defaultSort("created_at", DEFAULT_SORTING_ORDER)
            .allowedSortFields(ALLOWED_FILTER_MAP_FOR_APP_CREDENTIALS.keySet())
            .build();

    appCredentialsService
        .getApp(request)
        .onSuccess(
            paginatedResult -> {
              LOGGER.info(
                  "Fetched {} appCredentials for userId: {}", paginatedResult.data().size(), userId);
              ResponseBuilder.sendSuccess(
                  ctx, paginatedResult.data(), paginatedResult.paginationInfo(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void deleteApp(RoutingContext ctx) {
    LOGGER.trace("deleteAppCredentials() handler started");
    UUID userId = UUID.fromString(ctx.user().subject());
    UUID appId = UUID.fromString(ctx.pathParam("appId"));

    appCredentialsService
        .deleteApp(userId, appId)
        .onSuccess(
            handler -> {
              LOGGER.info("AppCredentials deleted for userId: {} and appId: {}", userId, appId);
              ResponseBuilder.sendSuccess(ctx, "Deleted app id successfully" ,urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void postDxUserInfo(RoutingContext ctx) {
    LOGGER.trace("postdxUserInfo() handler started");
    JsonObject appCredentials = ctx.body().asJsonObject();
    String appId = appCredentials.getString("appId");
    String appSecret = appCredentials.getString("appSecret");
    appCredentialsService
      .postDxUserInfoFromAppId(appId, appSecret)
      .onSuccess(
        dxUser -> {
          ResponseBuilder.sendSuccess(ctx, "Fetched user details from app id successfully", dxUser, urnGenerator);
        })
      .onFailure(ctx::fail);
  }

  public void changeAppStatus(RoutingContext ctx) {
    LOGGER.trace("deleteAppCredentials() handler started");
    UUID userId = UUID.fromString(ctx.user().subject());
    UUID appId = UUID.fromString(ctx.pathParam("appId"));

    String status = ctx.queryParam("status").stream().findFirst().orElse(null);

    appCredentialsService
      .changeAppStatus(userId, appId,status)
      .onSuccess(
        handler -> {
          LOGGER.info("AppCredentials status updated for userId: {} and appId : {}  with status: {}", userId, appId,status);
          ResponseBuilder.sendSuccess(ctx, "Updated app id status successfully" ,urnGenerator);
        })
      .onFailure(ctx::fail);
  }

  public List<String> extractRoles(User user) {
    List<String> roles = new ArrayList<>();
    JsonObject principal = user.principal();
    if (principal.containsKey("realm_access")) {
      JsonObject realmAccess = principal.getJsonObject("realm_access");
      if (realmAccess.containsKey("roles")) {
        roles.addAll(realmAccess.getJsonArray("roles").getList());
      }
    }
    return roles;
  }

  private String getHighestRole(Set<String> roles) {
    if (roles.contains("cos_admin")) return "cos_admin";
    if (roles.contains("org_admin")) return "org_admin";
    if (roles.contains("provider")) return "provider";
    return "consumer";
  }

}
