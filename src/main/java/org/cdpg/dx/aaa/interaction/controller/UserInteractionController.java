package org.cdpg.dx.aaa.interaction.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_GET_USER_INTERACTIONS;
import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_POST_USER_INTERACTION;
import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_SYNC_INTERACTION_METRICS;

import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.CREATED_AT;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.interaction.model.InteractionRequest;
import org.cdpg.dx.aaa.interaction.service.UserInteractionService;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;

public class UserInteractionController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(UserInteractionController.class);

  private static final Map<String, String> FILTER_MAP =
      Map.of(
          "entityId",
          "entity_id",
          "entityType",
          "entity_type",
          "actionType",
          "action_type",
          "value",
          "value");

  private final UserInteractionService service;
  private final URNGenerator urnGenerator;

  public UserInteractionController(UserInteractionService service, URNGenerator urnGenerator) {
    this.service = service;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering UserInteractionController routes");
    builder.operation(OP_POST_USER_INTERACTION).handler(this::handlePostUserInteractionRequest);
    builder.operation(OP_GET_USER_INTERACTIONS).handler(this::handleGetUserInteractionRequest);
    builder.operation(OP_SYNC_INTERACTION_METRICS).handler(this::handleSyncInteractionMetrics);
  }

  private void handlePostUserInteractionRequest(RoutingContext ctx) {
    try {
      InteractionRequest req = ctx.body().asJsonObject().mapTo(InteractionRequest.class);
      UUID userId = UUID.fromString(ctx.user().subject());

      service
          .handleInteraction(userId, req)
          .onSuccess(
              v ->
                  ResponseBuilder.sendSuccess(
                      ctx, "Interaction updated successfully", urnGenerator))
          .onFailure(
              err -> {
                LOGGER.error("POST /user/interactions failed", err);
                ctx.fail(err);
              });

    } catch (Exception e) {
      LOGGER.error("Invalid POST /user/interactions request", e);
      ctx.fail(e);
    }
  }

  private void handleGetUserInteractionRequest(RoutingContext ctx) {
    LOGGER.info("GET /user/interactions called");

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
                    ctx, result.data(), result.paginationInfo(), urnGenerator);
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
              v ->
                  ResponseBuilder.sendSuccess(
                      ctx, "Interaction metrics synced successfully", urnGenerator))
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
