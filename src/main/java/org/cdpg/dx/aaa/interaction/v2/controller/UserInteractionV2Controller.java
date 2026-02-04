package org.cdpg.dx.aaa.interaction.v2.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.*;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.CREATED_AT;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.interaction.v2.model.UserInteractionV2Request;
import org.cdpg.dx.aaa.interaction.v2.service.UserInteractionV2Service;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;

public class UserInteractionV2Controller implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(UserInteractionV2Controller.class);
  private static final Map<String, String> FILTER_MAP =
      Map.of("assetId", "asset_id", "assetType", "asset_type", "actionType", "action_type");
  private final UserInteractionV2Service service;
  private final URNGenerator urnGenerator;
  Handler<RoutingContext> syncInteractionMetricAccessHandler =
      AuthorizationHandler.forRoles(DxRole.COS_ADMIN);

  Handler<RoutingContext> interactionAccessHandler = AuthorizationHandler.forRoles(DxRole.CONSUMER);

  public UserInteractionV2Controller(UserInteractionV2Service service, URNGenerator urnGenerator) {
    this.service = service;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering UserInteractionController routes");
    builder
        .operation(OP_POST_USER_INTERACTION)
        .handler(interactionAccessHandler)
        .handler(this::handlePostUserInteractionRequest);
    builder
        .operation(OP_GET_USER_INTERACTIONS)
        .handler(interactionAccessHandler)
        .handler(this::handleGetUserInteractionRequest);
    builder
        .operation(OP_SYNC_INTERACTION_METRICS)
        .handler(syncInteractionMetricAccessHandler)
        .handler(this::handleSyncInteractionMetrics);
  }

  private void handlePostUserInteractionRequest(RoutingContext ctx) {
    LOGGER.info("handlePostUserInteractionRequest() method started");
    try {
      UserInteractionV2Request req =
          ctx.body().asJsonObject().mapTo(UserInteractionV2Request.class);

      UUID userId = UUID.fromString(ctx.user().subject());

      service
          .saveInteraction(userId, req)
          .onSuccess(
              v -> {
                LOGGER.info("Interaction updated successfully for user {}", userId);
                LOGGER.debug("Interaction details: {}", v.toJson());
                ResponseBuilder.sendSuccess(ctx, "Interaction recorded successfully", urnGenerator);
              })
          .onFailure(ctx::fail);

    } catch (Exception e) {
      ctx.fail(e);
    }
  }

  private void handleGetUserInteractionRequest(RoutingContext ctx) {
    LOGGER.info("handleGetUserInteractionRequest() method started");

    try {
      PaginatedRequest paginatedRequest =
          PaginationRequestBuilder.from(ctx)
              .allowedFiltersDbMap(FILTER_MAP)
              .apiToDbMap(FILTER_MAP)
              .additionalFilters(Map.of("user_id", ctx.user().subject()))
              .allowedTimeFields(Set.of(CREATED_AT))
              .build();
      LOGGER.debug("paginated request has been build ");
      service
          .getUserInteractions(paginatedRequest)
          .onSuccess(
              result -> {
                LOGGER.info("Fetched user interactions successfully");
                ResponseBuilder.sendSuccess(
                    ctx, result.result(), result.paginationInfo(), urnGenerator);
              })
          .onFailure(
              err -> {
                LOGGER.error("Failed to fetch user interaction {}", err.getMessage(), err);
                ctx.fail(err);
              });

    } catch (Exception e) {
      LOGGER.error("Invalid GET /user/interactions request:  {} ", e.getMessage(), e);
      ctx.fail(e);
    }
  }

  private void handleSyncInteractionMetrics(RoutingContext ctx) {
    LOGGER.info("GET /user/interactions/sync called");

    try {
      service
          .syncInteractionMetrics()
          .onSuccess(
              result ->
                  ResponseBuilder.sendSuccess(
                      ctx,
                      "Interaction metrics synced successfully",
                      result.toJson(),
                      urnGenerator))
          .onFailure(
              err -> {
                LOGGER.error("Failed to sync interaction metrics", err);
                ctx.fail(err);
              });
    } catch (Exception e) {
      LOGGER.error("Invalid GET /user/interactions/sync request:  {} ", e.getMessage(), e);
      ctx.fail(e);
    }
  }
}
