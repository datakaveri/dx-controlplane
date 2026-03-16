package org.cdpg.dx.aaa.interaction.v2.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.*;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.CREATED_AT;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.interaction.v2.enums.InteractionAction;
import org.cdpg.dx.aaa.interaction.v2.enums.InteractionAuditAction;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionDelta;
import org.cdpg.dx.aaa.interaction.v2.model.ProviderFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.UserInteractionV2Request;
import org.cdpg.dx.aaa.interaction.v2.service.UserInteractionV2Service;
import org.cdpg.dx.aaa.interaction.v2.util.InteractionAuditLogHelper;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;

public class UserInteractionV2Controller implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(UserInteractionV2Controller.class);
  private static final Map<String, String> FILTER_MAP =
      Map.of("assetId", "asset_id", "assetType", "asset_type", "actionType", "action_type");
  private static final Map<String, String> FEEDBACK_FILTER_MAP =
    Map.of("assetId", "asset_id", "actionSubType", "asset_subtype", "userId", "user_id");
  private static final Map<String, String> PROVIDER_FEEDBACK_FILTER_MAP =
    Map.of("assetId", "asset_id", "type", "type", "userId", "user_id");

  private final AuditingHandler auditingHandler;
  private final UserInteractionV2Service service;
  private final URNGenerator urnGenerator;
  Handler<RoutingContext> syncInteractionMetricAccessHandler =
      AuthorizationHandler.forRoles(DxRole.COS_ADMIN);

  Handler<RoutingContext> interactionAccessHandler = AuthorizationHandler.forRoles(DxRole.CONSUMER);
  Handler<RoutingContext> feedbackAccessHandler = AuthorizationHandler.forRoles(DxRole.CONSUMER);


  public UserInteractionV2Controller(
      AuditingHandler auditingHandler,
      UserInteractionV2Service service,
      URNGenerator urnGenerator) {
    this.auditingHandler = auditingHandler;
    this.service = service;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering UserInteractionController routes");
    builder
        .operation(OP_POST_USER_INTERACTION)
        .handler(auditingHandler::handleApiAudit)
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

     builder
        .operation(OP_POST_USER_FEEDBACK)
        .handler(interactionAccessHandler)
        .handler(this::handlePostUpdateUserFeedbackRequest);

     builder
          .operation(OP_GET_USER_FEEDBACK)
          .handler(interactionAccessHandler)
          .handler(this::handleGetUserFeedbackRequest);

    builder
       .operation(OP_DELETE_USER_FEEDBACK)
       .handler(interactionAccessHandler)
       .handler(this::handleDeleteUserFeedbackRequest);

    builder
      .operation(OP_POST_PROVIDER_FEEDBACK)
      .handler(feedbackAccessHandler)
      .handler(this::handlePostUpdateProviderFeedbackRequest);

    builder
      .operation(OP_GET_PROVIDER_FEEDBACK)
      .handler(feedbackAccessHandler)
      .handler(this::handleGetProviderFeedbackRequest);

    builder
      .operation(OP_DELETE_PROVIDER_FEEDBACK)
      .handler(feedbackAccessHandler)
      .handler(this::handleDeleteProviderFeedbackRequest);

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
              delta -> {
                LOGGER.info("Interaction updated successfully for user {}", userId);
                LOGGER.debug("Interaction details: {}", delta.toJson());

                InteractionAuditAction auditAction = resolveAuditAction(delta, req.action());

                if (auditAction != null) {
                  UserActivityAuditLogBuilder auditLog =
                      InteractionAuditLogHelper.buildItemAudit(ctx, delta.entityId(), auditAction);
                  CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);
                }

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

  private InteractionAuditAction resolveAuditAction(
      InteractionDelta delta, InteractionAction requestAction) {

    // Bookmark actions are explicit
    if (delta.oldBookmarked() != delta.newBookmarked()) {
      return delta.newBookmarked()
          ? InteractionAuditAction.BOOKMARK
          : InteractionAuditAction.UNBOOKMARK;
    }

    // Vote actions based on actual transition
    if (!delta.oldLiked() && delta.newLiked()) {
      return InteractionAuditAction.LIKE;
    }

    if (!delta.oldDisliked() && delta.newDisliked()) {
      return InteractionAuditAction.DISLIKE;
    }

    // Neutral (vote removed)
    if ((delta.oldLiked() || delta.oldDisliked()) && !delta.newLiked() && !delta.newDisliked()) {
      return InteractionAuditAction.NEUTRAL;
    }

    // No-op → nothing to audit
    return null;
  }


  private void handlePostUpdateUserFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("POST /user/feedback called");
    try {
      JsonObject req = ctx.body().asJsonObject();
      UUID userId = UUID.fromString(ctx.user().subject());
      req.put("user_id",userId.toString());

      UserFeedback userFeedback = UserFeedback.fromJson(req);

      service
        .postUserFeedback(userFeedback)
        .onSuccess(
          v ->
            ResponseBuilder.sendSuccess(
              ctx, "Interaction updated successfully", urnGenerator))
        .onFailure(
          err -> {
            LOGGER.error("POST /user/feedback failed", err);
            ctx.fail(err);
          });

    } catch (Exception e) {
      LOGGER.error("Invalid POST /user/feedback request", e);
      ctx.fail(e);
    }
  }

  private void handleGetUserFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("GET /user/feedback called");

    try {
      PaginatedRequest paginatedRequest =
        PaginationRequestBuilder.from(ctx)
          .allowedFiltersDbMap(FEEDBACK_FILTER_MAP)
          .apiToDbMap(FEEDBACK_FILTER_MAP)
//          .additionalFilters(Map.of("user_id", ctx.user().subject()))
          .allowedTimeFields(Set.of(CREATED_AT))
          .build();
      LOGGER.debug("paginated request has been build ");
      service
        .getUserFeedback(paginatedRequest)
        .onSuccess(
          result -> {
            LOGGER.info("Fetched user feedbacks successfully");
            ResponseBuilder.sendSuccess(
              ctx, result.data(), result.paginationInfo(), urnGenerator);
          })
        .onFailure(
          err -> {
            LOGGER.error("Failed to fetch user feedbacks {}", err.getMessage(), err);
            ctx.fail(err);
          });

    } catch (Exception e) {
      LOGGER.error("Invalid GET /user/feedback request:  {} ", e.getMessage(), e);
      ctx.fail(e);
    }

  }

  private void handleDeleteUserFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("DELETE /user/feedback?id= called");
    try {
      String idParam = ctx.queryParam("id").toString();

      if (idParam == null) {
        ctx.fail(new IllegalArgumentException("Missing required query parameter: id"));
        return;
      }

      UUID reqId;
      try {
        reqId = UUID.fromString(idParam);
      } catch (IllegalArgumentException e) {
        ctx.fail(new IllegalArgumentException("Invalid UUID format for parameter: id"));
        return;
      }

      UUID userId = UUID.fromString(ctx.user().subject());

      service
        .deleteUserFeedback(reqId,userId)
        .onSuccess(
          v ->
            ResponseBuilder.sendSuccess(
              ctx, "Interaction updated successfully", urnGenerator))
        .onFailure(
          err -> {
            LOGGER.error("Delete /user/feedback failed", err);
            ctx.fail(err);
          });

    } catch (Exception e) {
      LOGGER.error("Invalid POST /user/feedback request", e);
      ctx.fail(e);
    }
  }

  private void handlePostUpdateProviderFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("POST /provider/feedback called");
    try {
      JsonObject req = ctx.body().asJsonObject();
      UUID userId = UUID.fromString(ctx.user().subject());
      req.put("user_id",userId.toString());

      ProviderFeedback providerFeedback = ProviderFeedback.fromJson(req);

      service
        .postProviderFeedback(providerFeedback)
        .onSuccess(
          v ->
            ResponseBuilder.sendSuccess(
              ctx, "Interaction updated successfully", urnGenerator))
        .onFailure(
          err -> {
            LOGGER.error("POST /provider/feedback failed", err);
            ctx.fail(err);
          });

    } catch (Exception e) {
      LOGGER.error("Invalid POST /user/feedback request", e);
      ctx.fail(e);
    }
  }
//
  private void handleGetProviderFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("GET /provider/feedback called");

    try {
      PaginatedRequest paginatedRequest =
        PaginationRequestBuilder.from(ctx)
          .allowedFiltersDbMap(PROVIDER_FEEDBACK_FILTER_MAP)
          .apiToDbMap(PROVIDER_FEEDBACK_FILTER_MAP)
//          .additionalFilters(Map.of("user_id", ctx.user().subject()))
          .allowedTimeFields(Set.of(CREATED_AT))
          .build();
      LOGGER.debug("paginated request has been build ");
      service
        .getProviderFeedback(paginatedRequest)
        .onSuccess(
          result -> {
            LOGGER.info("Fetched provider feedbacks successfully");
            ResponseBuilder.sendSuccess(
              ctx, result.data(), result.paginationInfo(), urnGenerator);
          })
        .onFailure(
          err -> {
            LOGGER.error("Failed to fetch provider feedbacks {}", err.getMessage(), err);
            ctx.fail(err);
          });

    } catch (Exception e) {
      LOGGER.error("Invalid GET /provider/feedback request:  {} ", e.getMessage(), e);
      ctx.fail(e);
    }

  }

  private void handleDeleteProviderFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("DELETE /user/feedback called");
    try {
      String idParam = ctx.queryParam("id").getFirst();

      if (idParam == null) {
        ctx.fail(new IllegalArgumentException("Missing required query parameter: id"));
        return;
      }

      UUID reqId;
      try {
        reqId = UUID.fromString(idParam);
      } catch (IllegalArgumentException e) {
        ctx.fail(new IllegalArgumentException("Invalid UUID format for parameter: id"));
        return;
      }

      UUID userId = UUID.fromString(ctx.user().subject());

      service
        .deleteProviderFeedback(reqId,userId)
        .onSuccess(
          v ->
            ResponseBuilder.sendSuccess(
              ctx, "Interaction deleted successfully", urnGenerator))
        .onFailure(
          err -> {
            LOGGER.error("Delete /user/feedback failed", err);
            ctx.fail(err);
          });

    } catch (Exception e) {
      LOGGER.error("Invalid Delete /user/feedback request", e);
      ctx.fail(e);
    }
  }

}
