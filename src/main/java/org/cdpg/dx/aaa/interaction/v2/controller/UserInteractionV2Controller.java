package org.cdpg.dx.aaa.interaction.v2.controller;

import static org.cdpg.dx.aaa.activity.util.ActivityConstants.CREATED_AT;
import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_GET_USER_INTERACTIONS;
import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_POST_USER_INTERACTION;

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
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;

public class UserInteractionV2Controller implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(UserInteractionV2Controller.class);

  private final UserInteractionV2Service service;
  private final URNGenerator urnGenerator;
  private static final Map<String, String> FILTER_MAP =
      Map.of("entityId", "entity_id", "entityType", "entity_type", "actionType", "action_type");

  public UserInteractionV2Controller(UserInteractionV2Service service, URNGenerator urnGenerator) {
    this.service = service;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering UserInteractionController routes");
    builder.operation(OP_POST_USER_INTERACTION).handler(this::handlePostUserInteractionRequest);
    builder.operation(OP_GET_USER_INTERACTIONS).handler(this::handleGetUserInteractionRequest);
  }

  private void handlePostUserInteractionRequest(RoutingContext ctx) {
    try {
      UserInteractionV2Request req =
          ctx.body().asJsonObject().mapTo(UserInteractionV2Request.class);

      UUID userId = UUID.fromString(ctx.user().subject());

      service.SaveIteraction(userId, req)
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
}
