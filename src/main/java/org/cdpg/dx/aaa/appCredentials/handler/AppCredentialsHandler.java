package org.cdpg.dx.aaa.appCredentials.handler;

import static org.cdpg.dx.aaa.appCredentials.util.Constants.*;
import static org.cdpg.dx.aaa.bookmarks.util.Constants.ALLOWED_FILTER_MAP_FOR_BOOKMARK_REQUEST;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;

public class AppCredentialsHandler {
  private static final Logger LOGGER = LogManager.getLogger(AppCredentialsHandler.class);
  private final AppCredentialsService appCredentialsService;
  private final URNGenerator urnGenerator;

  public AppCredentialsHandler(AppCredentialsService appCredentialsService, URNGenerator urnGenerator) {
    this.appCredentialsService = appCredentialsService;
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
    try {
      userId = UUID.fromString(user.subject());
    } catch (IllegalArgumentException e) {
      LOGGER.error("Invalid user subject for UUID: {}", user.subject(), e);
      ctx.fail(new DxBadRequestException("Invalid user subject for UUID"));
      return;
    }



    JsonObject body = ctx.body().asJsonObject();

    body.put(USER_ID,userId.toString());
    body.getString(EXPIRY_AT);

    AppCredentials appCredentials = AppCredentials.fromJson(body);

    appCredentialsService
      .createApp(appCredentials)
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
}
