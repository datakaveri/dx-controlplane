package org.cdpg.dx.aaa.leaderboard.controller;

import static org.cdpg.dx.aaa.activity.util.ActivityConstants.CREATED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.apiserver.OperationIds;
import org.cdpg.dx.aaa.leaderboard.model.*;
import org.cdpg.dx.aaa.leaderboard.service.LeaderboardService;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;

public class LeaderboardController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(LeaderboardController.class);
  private final LeaderboardService leaderboardService;
  private final URNGenerator urnGenerator;

  public LeaderboardController(LeaderboardService leaderboardService, URNGenerator urnGenerator) {
    this.leaderboardService = leaderboardService;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering LeaderboardController routes");
    builder.operation(OperationIds.OP_GET_ORG_LEADERBOARD).handler(this::getOrgLeaderboardHandler);
    builder
        .operation(OperationIds.OP_GET_PROVIDER_LEADERBOARD)
        .handler(this::getProviderLeaderboardHandler);
    builder
        .operation(OperationIds.OP_GET_ASSET_LEADERBOARD)
        .handler(this::getAssetLeaderboardHandler);
  }

  private void getOrgLeaderboardHandler(RoutingContext ctx) {
    LOGGER.debug("Handling getOrgLeaderboard request");

    Map<String, String> allowedFiltersDbMap =
        Map.of(
            "assetType",
            "asset_type",
            "organizationType",
            "organization_type",
            "timeAt",
            "created_at",
            "endTimeAt",
            "created_at",
            "page",
            "page",
            "size",
            "size",
            "sort",
            "sort");

    try {
      PaginatedRequest paginatedRequest =
          PaginationRequestBuilder.from(ctx)
              .allowedFiltersDbMap(allowedFiltersDbMap)
              // .allowedSortFields(...)   // configure as needed
              // .apiToDbMap(...)          // configure as needed
              .build();
      leaderboardService
          .getOrgLeaderboard(paginatedRequest)
          .onSuccess(
              response ->
                  ResponseBuilder.sendSuccess(
                      ctx,
                      response != null ? response.getData() : Collections.emptyList(),
                      response != null ? response.getPaginationInfo() : null,
                      urnGenerator))
          .onFailure(
              err -> {
                LOGGER.error("Error handling getOrgLeaderboard request", err);
                ctx.fail(err);
              });
    } catch (Exception e) {
      LOGGER.error("Error handling getOrgLeaderboard request", e);
      ctx.fail(e);
    }
  }

  private void getProviderLeaderboardHandler(RoutingContext ctx) {
    LOGGER.debug("Handling getProviderLeaderboard request");
    Map<String, String> allowedFiltersDbMap =
        Map.of(
            "accessPolicy",
            "access_policy",
            "assetType",
            "asset_type",
            "organizationType",
            "organization_type",
            "timeAt",
            "created_at",
            "endTimeAt",
            "created_at",
            "page",
            "page",
            "size",
            "size",
            "sort",
            "sort");
    try {
      PaginatedRequest paginatedRequest =
          PaginationRequestBuilder.from(ctx)
              .allowedFiltersDbMap(allowedFiltersDbMap) // configure as needed
              // .allowedSortFields(...)   // configure as needed
              // .apiToDbMap(...)          // configure as needed
              .build();
      leaderboardService
          .getProviderLeaderboard(paginatedRequest)
          .onSuccess(
              response ->
                  ResponseBuilder.sendSuccess(
                      ctx,
                      response != null ? response.getData() : Collections.emptyList(),
                      response != null ? response.getPaginationInfo() : null,
                      urnGenerator))
          .onFailure(
              err -> {
                LOGGER.error("Error handling getProviderLeaderboard request", err);
                ctx.fail(err);
              });
    } catch (Exception e) {
      LOGGER.error("Error handling getProviderLeaderboard request", e);
      ctx.fail(e);
    }
  }

  private void getAssetLeaderboardHandler(RoutingContext ctx) {
    LOGGER.debug("Handling getAssetLeaderboard request");

    Map<String, String> allowedFiltersDbMap =
        Map.of(
            "accessPolicy",
            "accessPolicy",
            "assetType",
            "assetType",
            "organizationType",
            "organizationType",
            "sector",
            "sector");

    Map<String, String> apiToDbmap =
        Map.of(
            "accessPolicy",
            "accessPolicy",
            "assetType",
            "assetType",
            "organizationType",
            "organizationType",
            "sector",
            "sector",
            "downloads",
            "downloads",
            "likes",
            "likes",
            "dislikes",
            "dislikes",
            "views",
            "views");

    Set<String> allowedSortFields = Set.of("downloads", "likes", "dislikes", "views");

    try {
      PaginatedRequest paginatedRequest =
          PaginationRequestBuilder.from(ctx)
              .allowedFiltersDbMap(allowedFiltersDbMap) // configure as needed
              .apiToDbMap(apiToDbmap)
              .allowedSortFields(allowedSortFields)
              .allowedTimeFields(Set.of(CREATED_AT))
              .defaultTimeField(CREATED_AT)
              .allowedSortFields(allowedSortFields)
              .defaultSort("downloads", DEFAULT_SORTING_ORDER)
              .build();
      leaderboardService
          .getAssetLeaderboard(paginatedRequest)
          .onSuccess(
              response ->
                  ResponseBuilder.sendSuccess(
                      ctx,
                      response != null ? response.getData() : Collections.emptyList(),
                      response != null ? response.getPaginationInfo() : null,
                      urnGenerator))
          .onFailure(
              err -> {
                LOGGER.error(
                    "Error handling getAssetLeaderboard request: {}", err.getMessage(), err);
                ctx.fail(err);
              });
    } catch (Exception e) {
      LOGGER.error("Error handling getAssetLeaderboard request: {}", e.getMessage(), e);
      ctx.fail(e);
    }
  }
}
