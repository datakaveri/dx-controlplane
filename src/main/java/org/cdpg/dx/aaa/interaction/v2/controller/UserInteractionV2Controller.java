package org.cdpg.dx.aaa.interaction.v2.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.*;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.CREATED_AT;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.interaction.v2.enums.InteractionAction;
import org.cdpg.dx.aaa.interaction.v2.enums.InteractionAuditAction;
import org.cdpg.dx.aaa.interaction.v2.enums.ProviderFeedbackType;
import org.cdpg.dx.aaa.interaction.v2.model.FeedbackApprovalRequest;
import org.cdpg.dx.aaa.interaction.v2.model.FeedbackStatus;
import org.cdpg.dx.aaa.interaction.v2.model.InteractionDelta;
import org.cdpg.dx.aaa.interaction.v2.model.ProviderFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedbackResult;
import org.cdpg.dx.aaa.interaction.v2.model.UserInteractionV2Request;
import org.cdpg.dx.aaa.interaction.v2.service.UserInteractionV2Service;
import org.cdpg.dx.aaa.interaction.v2.util.InteractionAuditLogHelper;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.ScopeRule;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;

public class UserInteractionV2Controller implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(UserInteractionV2Controller.class);
  private static final Map<String, String> FILTER_MAP =
      Map.of("assetId", "asset_id", "assetType", "asset_type", "actionType", "action_type");
  private static final Map<String, String> FEEDBACK_FILTER_MAP =
      Map.of(
          "assetId", "asset_id",
          "actionSubtype", "action_subtype",
          "userId", "user_id",
          "rating", "entity_rating",
          "ratingCreatedAt", "feedback_created_at",
          "feedbackStatus", "feedback_status",
          "feedbackComment", "feedback_comment",
          "feedbackStatusUpdatedAt", "feedback_status_updated_at");
  private static final Set<String> FEEDBACK_SORT_FIELDS = Set.of("ratingCreatedAt");
  private static final Map<String, String> PROVIDER_FEEDBACK_FILTER_MAP =
      Map.of("assetId", "asset_id", "type", "type");

  private final AuditingHandler auditingHandler;
  private final UserInteractionV2Service service;
  private final URNGenerator urnGenerator;

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

    var userScopedAccess = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);
    var adminSyncAccess = AuthorizationHandler.forScopes(Scopes.USER_MANAGEMENT);
    var providerFeedbackAccess =
        AuthorizationHandler.forScopesWithContext(
            ScopeRule.self(Scopes.OWN_ASSET_MANAGEMENT),
            ScopeRule.org(Scopes.ORG_ASSET_MANAGEMENT));
    Handler<RoutingContext> cosAdminAccess =
        AuthorizationHandler.forScopes(Scopes.ASSET_MANAGEMENT);

    builder
        .operation(OP_POST_USER_INTERACTION)
        .handler(auditingHandler::handleApiAudit)
        .handler(userScopedAccess)
        .handler(this::handlePostUserInteractionRequest);
    builder
        .operation(OP_GET_USER_INTERACTIONS)
        .handler(userScopedAccess)
        .handler(this::handleGetUserInteractionRequest);
    builder
        .operation(OP_SYNC_INTERACTION_METRICS)
        .handler(adminSyncAccess)
        .handler(this::handleSyncInteractionMetrics);

    builder
        .operation(OP_POST_USER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(userScopedAccess)
        .handler(this::handlePostUserFeedbackRequest);

    builder
        .operation(OP_PUT_USER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(userScopedAccess)
        .handler(this::handlePutUserFeedbackRequest);

    builder
        .operation(OP_PLATFORM_PUT_USER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(cosAdminAccess)
        .handler(this::handleUpdateUserFeedbackStatusRequest);

    builder
        .operation(OP_GET_USER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(userScopedAccess)
        .handler(this::handleGetUserFeedbackRequest);

    builder
        .operation(OP_GET_APPROVED_PLATFORM_USER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(userScopedAccess)
        .handler(this::handleGetApprovedPlatformUserFeedbackRequest);

    builder
        .operation(OP_GET_PLATFORM_USER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(cosAdminAccess)
        .handler(this::handleGetPlatformUsersFeedbackRequests);

    builder
        .operation(OP_DELETE_USER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(userScopedAccess)
        .handler(this::handleDeleteUserFeedbackRequest);

    builder
        .operation(OP_POST_PROVIDER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(providerFeedbackAccess)
        .handler(this::handlePostProviderFeedbackRequest);

    builder
        .operation(OP_PUT_PROVIDER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(providerFeedbackAccess)
        .handler(this::handlePutProviderFeedbackRequest);

    builder
        .operation(OP_GET_PROVIDER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(this::handleGetProviderFeedbackRequest);

    builder
        .operation(OP_DELETE_PROVIDER_FEEDBACK)
        .handler(auditingHandler::handleApiAudit)
        .handler(providerFeedbackAccess)
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
                      InteractionAuditLogHelper.buildItemAudit(
                          ctx, delta.entityId(), auditAction, delta);
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

  private void handlePostUserFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("POST /user/feedback called");

    try {
      JsonObject req = ctx.body().asJsonObject();

      UUID userId = UUID.fromString(ctx.user().subject());
      req.put("userId", userId.toString());

      UserFeedback userFeedback = UserFeedback.fromRequestJson(req);

      service
          .postUserFeedback(userFeedback)
          .onSuccess(
              feedback -> {
                UserActivityAuditLogBuilder auditLog =
                    InteractionAuditLogHelper.buildFeedbackAudit(
                        ctx,
                        userFeedback.assetId() != null ? userFeedback.assetId().toString() : null,
                        InteractionAuditAction.RATING);

                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);

                ResponseBuilder.sendSuccess(ctx, "Feedback submitted successfully", urnGenerator);
              })
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

  private void handlePutUserFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("PUT /user/feedback called");

    try {
      JsonObject req = ctx.body().asJsonObject();

      UUID userId = UUID.fromString(ctx.user().subject());
      req.put("userId", userId.toString());

      UserFeedback userFeedback = UserFeedback.fromRequestJson(req);

      service
          .putUserFeedback(userFeedback)
          .onSuccess(
              feedback -> {
                UserActivityAuditLogBuilder auditLog =
                    InteractionAuditLogHelper.buildFeedbackAudit(
                        ctx,
                        userFeedback.assetId() != null ? userFeedback.assetId().toString() : null,
                        InteractionAuditAction.RATING);

                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);

                ResponseBuilder.sendSuccess(ctx, "Feedback updated successfully", urnGenerator);
              })
          .onFailure(
              err -> {
                LOGGER.error("PUT /user/feedback failed", err);
                ctx.fail(err);
              });

    } catch (Exception e) {
      LOGGER.error("Invalid PUT /user/feedback request", e);
      ctx.fail(e);
    }
  }

  private void handleUpdateUserFeedbackStatusRequest(RoutingContext ctx) {

    LOGGER.info("PUT /feedback/{id} called");

    try {

      String feedbackIdParam = ctx.pathParam("id");

      if (feedbackIdParam == null) {
        ctx.fail(new IllegalArgumentException("Missing required path parameter: id"));
        return;
      }

      UUID feedbackId = UUID.fromString(feedbackIdParam);

      JsonObject req = ctx.body().asJsonObject();

      FeedbackApprovalRequest approvalRequest = FeedbackApprovalRequest.fromJson(req);

      service
          .updateFeedbackStatus(feedbackId, approvalRequest.status(), approvalRequest.comment())
          .onSuccess(
              feedback -> {
                InteractionAuditAction auditAction =
                    approvalRequest.status() == FeedbackStatus.APPROVED
                        ? InteractionAuditAction.APPROVE_RATING
                        : InteractionAuditAction.REJECT_RATING;

                UserActivityAuditLogBuilder auditLog =
                    InteractionAuditLogHelper.buildFeedbackAudit(
                        ctx,
                        feedback.assetId() != null ? feedback.assetId().toString() : null,
                        auditAction);

                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);

                ResponseBuilder.sendSuccess(ctx, feedback, urnGenerator);
              })
          .onFailure(
              err -> {
                LOGGER.error("PUT /feedback/{id} failed", err);
                ctx.fail(err);
              });

    } catch (Exception e) {

      LOGGER.error("Invalid PUT /feedback/{id} request", e);

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
              .additionalFilters(Map.of("user_id", ctx.user().subject()))
              .allowedSortFields(FEEDBACK_SORT_FIELDS)
              .defaultSort("feedback_created_at", "desc")
              .defaultTimeField("feedback_created_at")
              .build();
      LOGGER.debug("paginated request has been build ");

      List<String> assetIdParams = ctx.queryParam("assetId");
      String assetIdParam =
          assetIdParams != null && !assetIdParams.isEmpty()
              ? assetIdParams.getFirst()
              : null;
      service
          .getUserFeedback(paginatedRequest)
          .onSuccess(
              result -> {
                LOGGER.info("Fetched user feedbacks successfully");

                UserActivityAuditLogBuilder auditLog =
                    InteractionAuditLogHelper.buildFeedbackAudit(
                        ctx, assetIdParam, InteractionAuditAction.VIEW_RATING);
                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);

                ResponseBuilder.sendSuccess(
                    ctx,
                    new UserFeedbackResult(result.summary(), result.data()),
                    result.paginationInfo(),
                    urnGenerator);
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

  private void handleGetApprovedPlatformUserFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("GET /user/feedback/approved called");

    try {
      PaginatedRequest paginatedRequest =
          PaginationRequestBuilder.from(ctx)
              .allowedFiltersDbMap(FEEDBACK_FILTER_MAP)
              .apiToDbMap(FEEDBACK_FILTER_MAP)
              .allowedSortFields(FEEDBACK_SORT_FIELDS)
              .defaultSort("feedback_created_at", "desc")
              .defaultTimeField("feedback_created_at")
              .additionalFilters(Map.of("feedback_status", FeedbackStatus.APPROVED.name()))
              .build();

      LOGGER.info("Approved feedback pagination request: {}", paginatedRequest);

      List<String> assetIdParams = ctx.queryParam("assetId");
      String assetIdParam =
          assetIdParams != null && !assetIdParams.isEmpty()
              ? assetIdParams.getFirst()
              : null;
      service
          .getApprovedPlatformUserFeedbacks(paginatedRequest)
          .onSuccess(
              result -> {
                LOGGER.info("Fetched approved user feedbacks successfully");

                UserActivityAuditLogBuilder auditLog =
                    InteractionAuditLogHelper.buildFeedbackAudit(
                        ctx, assetIdParam, InteractionAuditAction.VIEW_RATING);

                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);

                ResponseBuilder.sendSuccess(
                    ctx,
                    new UserFeedbackResult(result.summary(), result.data()),
                    result.paginationInfo(),
                    urnGenerator);
              })
          .onFailure(
              err -> {
                LOGGER.error("Failed to fetch approved user feedbacks {}", err.getMessage(), err);
                ctx.fail(err);
              });

    } catch (Exception e) {
      LOGGER.error("Invalid GET /user/feedback/approved request: {}", e.getMessage(), e);
      ctx.fail(e);
    }
  }

  private void handleGetPlatformUsersFeedbackRequests(RoutingContext ctx) {
    LOGGER.info("GET /user/feedback called");

    try {
      PaginatedRequest paginatedRequest =
          PaginationRequestBuilder.from(ctx)
              .allowedFiltersDbMap(FEEDBACK_FILTER_MAP)
              .apiToDbMap(FEEDBACK_FILTER_MAP)
              .allowedSortFields(FEEDBACK_SORT_FIELDS)
              .defaultSort("feedback_created_at", "desc")
              .defaultTimeField("feedback_created_at")
              .build();
      LOGGER.debug("paginated request has been build ");

      List<String> assetIdParams = ctx.queryParam("assetId");
      String assetIdParam =
          assetIdParams != null && !assetIdParams.isEmpty()
              ? assetIdParams.getFirst()
              : null;
      service
          .getPlatformUsersFeedbacks(paginatedRequest)
          .onSuccess(
              result -> {
                LOGGER.info("Fetched user feedbacks successfully");

                UserActivityAuditLogBuilder auditLog =
                    InteractionAuditLogHelper.buildFeedbackAudit(
                        ctx, assetIdParam, InteractionAuditAction.VIEW_RATING);
                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);

                ResponseBuilder.sendSuccess(
                    ctx,
                    new UserFeedbackResult(result.summary(), result.data()),
                    result.paginationInfo(),
                    urnGenerator);
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
    LOGGER.info("DELETE /user/feedback?assetId= called");
    try {
      String assetIdParam = ctx.queryParam("assetId").getFirst();

      if (assetIdParam == null) {
        ctx.fail(new IllegalArgumentException("Missing required query parameter: assetId"));
        return;
      }

      UUID assetId = UUID.fromString(assetIdParam);
      UUID userId = UUID.fromString(ctx.user().subject());

      service
          .deleteUserFeedback(userId, assetId)
          .onSuccess(
              v -> {
                UserActivityAuditLogBuilder auditLog =
                    InteractionAuditLogHelper.buildFeedbackAudit(
                        ctx, assetId.toString(), InteractionAuditAction.REMOVE_RATING);
                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);

                ResponseBuilder.sendSuccess(
                    ctx, "Feedback deleted successfully", urnGenerator);
              })
          .onFailure(
              err -> {
                LOGGER.error("Delete /user/feedback failed", err);
                ctx.fail(err);
              });

    } catch (Exception e) {
      LOGGER.error("Invalid DELETE /user/feedback request", e);
      ctx.fail(e);
    }
  }

  private void handlePostProviderFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("POST /provider/feedback called");
    handleUpsertProviderFeedbackRequest(
        ctx,
        "POST",
        InteractionAuditAction.SUBMIT_PROVIDER_FEEDBACK,
        "Feedback submitted successfully");
  }

  private void handlePutProviderFeedbackRequest(RoutingContext ctx) {
    LOGGER.info("PUT /provider/feedback called");
    handleUpsertProviderFeedbackRequest(
        ctx,
        "PUT",
        InteractionAuditAction.UPDATE_PROVIDER_FEEDBACK,
        "Feedback updated successfully");
  }

  // POST (create) and PUT (update) are separate operations for callers, but both
  // resolve to the same upsert underneath (provider_feedback is keyed by
  // asset_id+type, see V81) - either call replaces the full data list for that
  // asset+type in one shot, not a partial merge. Message/audit action are chosen
  // statically per verb, matching /user/feedback's POST-vs-PUT convention, rather
  // than inferred from the DB result.
  private void handleUpsertProviderFeedbackRequest(
      RoutingContext ctx,
      String httpMethod,
      InteractionAuditAction auditAction,
      String successMessage) {
    try {
      JsonObject req = ctx.body().asJsonObject();
      UUID userId = UUID.fromString(ctx.user().subject());
      req.put("userId", userId.toString());

      ProviderFeedback providerFeedback = ProviderFeedback.fromRequestJson(req);

      service
          .postProviderFeedback(providerFeedback)
          .onSuccess(
              savedFeedback -> {
                UserActivityAuditLogBuilder auditLog =
                    InteractionAuditLogHelper.buildFeedbackAudit(
                        ctx,
                        providerFeedback.assetId() != null
                            ? providerFeedback.assetId().toString()
                            : null,
                        auditAction);
                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);

                ResponseBuilder.sendSuccess(ctx, successMessage, urnGenerator);
              })
          .onFailure(
              err -> {
                LOGGER.error("{} /provider/feedback failed", httpMethod, err);
                ctx.fail(err);
              });

    } catch (Exception e) {
      LOGGER.error("Invalid {} /provider/feedback request", httpMethod, e);
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
              .allowedTimeFields(Set.of(CREATED_AT))
              //.additionalFilters(Map.of("user_id", ctx.user().subject()))
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
    LOGGER.info("DELETE /provider/feedback?assetId=&type= called");
    try {
      String assetIdParam = ctx.queryParam("assetId").getFirst();
      String typeParam = ctx.queryParam("type").getFirst();

      if (assetIdParam == null) {
        ctx.fail(new IllegalArgumentException("Missing required query parameter: assetId"));
        return;
      }

      if (typeParam == null) {
        ctx.fail(new IllegalArgumentException("Missing required query parameter: type"));
        return;
      }

      UUID assetId;
      ProviderFeedbackType type;
      try {
        assetId = UUID.fromString(assetIdParam);
        type = ProviderFeedbackType.fromString(typeParam);
      } catch (IllegalArgumentException e) {
        ctx.fail(new IllegalArgumentException("Invalid value for parameter: assetId or type"));
        return;
      }

      UUID userId = UUID.fromString(ctx.user().subject());

      service
          .deleteProviderFeedback(userId, assetId, type)
          .onSuccess(
              v -> {
                UserActivityAuditLogBuilder auditLog =
                    InteractionAuditLogHelper.buildFeedbackAudit(
                        ctx, assetId.toString(), InteractionAuditAction.REMOVE_PROVIDER_FEEDBACK);
                CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);

                ResponseBuilder.sendSuccess(
                    ctx, "Interaction deleted successfully", urnGenerator);
              })
          .onFailure(
              err -> {
                LOGGER.error("Delete /provider/feedback failed", err);
                ctx.fail(err);
              });

    } catch (Exception e) {
      LOGGER.error("Invalid DELETE /provider/feedback request", e);
      ctx.fail(e);
    }
  }
}
