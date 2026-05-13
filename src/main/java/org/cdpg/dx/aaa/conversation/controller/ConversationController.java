package org.cdpg.dx.aaa.conversation.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.*;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.CREATED_AT;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.conversation.model.ConversationMessage;
import org.cdpg.dx.aaa.conversation.model.ConversationUpdateRequest;
import org.cdpg.dx.aaa.conversation.service.ConversationService;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.model.Scopes;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;

public class ConversationController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(ConversationController.class);
  private static final Map<String, String> MESSAGE_FILTER_MAP =
      Map.of("requestType", "request_type");
  private static final Map<String, String> REQUEST_TYPE_FILTER_MAP =
      Map.of(
          "type_key", "type_key",
          "display_name", "display_name",
          "is_active", "is_active");

  private final ConversationService service;
  private final URNGenerator urnGenerator;

  public ConversationController(
      ConversationService service,
      URNGenerator urnGenerator) {
    this.service = service;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {

    var selfAccess = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);
    var orgAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_USER_MANAGEMENT);
    var cosAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_MANAGEMENT);

    builder
        .operation(OP_GET_CONVERSATION_MESSAGES)
        .handler(selfAccess)
        .handler(this::handleGetAllMessages);
    builder
        .operation(OP_GET_CONVERSATION_MESSAGE)
        .handler(selfAccess)
        .handler(this::handleGetSingleMessage);

    builder
        .operation(OP_CREATE_CONVERSATION_MESSAGE)
        .handler(cosAdminAccess)
        .handler(this::handleCreateMessage);

    builder
        .operation(OP_REPLY_CONVERSATION_MESSAGE)
        .handler(cosAdminAccess)
        .handler(this::handleReplyToMessage);

    builder.operation(OP_UPDATE_CONVERSATION_MESSAGE).handler(this::handleUpdateMessage);

    builder.operation(OP_DELETE_CONVERSATION_MESSAGE).handler(this::handleDeleteMessage);
  }

  private void handleGetAllMessages(RoutingContext ctx) {
    try {
      String requestType = ctx.pathParam("request_type");

      PaginatedRequest paginatedRequest =
          PaginationRequestBuilder.from(ctx)
              .additionalFilters(Map.of("request_type", requestType))
              .allowedFiltersDbMap(MESSAGE_FILTER_MAP)
              .apiToDbMap(MESSAGE_FILTER_MAP)
              .allowedTimeFields(Set.of(CREATED_AT))
              .build();

      service
          .getAllMessages(requestType, paginatedRequest)
          .onSuccess(
              result ->
                  ResponseBuilder.sendSuccess(
                      ctx, result.data(), result.paginationInfo(), urnGenerator))
          .onFailure(ctx::fail);
    } catch (Exception e) {
      LOGGER.error("Failed to fetch conversation messages", e);
      ctx.fail(e);
    }
  }

  private void handleGetSingleMessage(RoutingContext ctx) {
    try {
      String requestTypeId = ctx.pathParam("request_type");
      UUID messageId = UUID.fromString(ctx.pathParam("msg_id"));

      service
          .getSingleMessage(requestTypeId, messageId)
          .onSuccess(result -> ResponseBuilder.sendSuccess(ctx, result, urnGenerator))
          .onFailure(ctx::fail);
    } catch (Exception e) {
      LOGGER.error("Failed to fetch a conversation message", e);
      ctx.fail(e);
    }
  }

  // /iudx/v2/requests/{request_id}/conversations
  private void handleCreateMessage(RoutingContext ctx) {
    try {
      String requestType = ctx.pathParam("request_type");
      UUID userId = UUID.fromString(ctx.user().subject());

      JsonObject message = ctx.body().asJsonObject();
      message.put("request_type", requestType);
      message.put("sender_id", userId.toString());

      ConversationMessage request = ConversationMessage.fromJson(message);

      service
          .createMessage(request)
          .onSuccess(result -> ResponseBuilder.sendSuccess(ctx, result, urnGenerator))
          .onFailure(ctx::fail);
    } catch (Exception e) {
      LOGGER.error("Failed to create a conversation message", e);
      ctx.fail(e);
    }
  }

  private void handleReplyToMessage(RoutingContext ctx) {
    try {
      String requestType = ctx.pathParam("request_type");
      UUID parentMsgId = UUID.fromString(ctx.pathParam("msg_id"));
      UUID userId = UUID.fromString(ctx.user().subject());

      JsonObject message = ctx.body().asJsonObject();
      message.put("request_type", requestType);
      message.put("sender_id", userId.toString());
      message.put("parent_msg_id", parentMsgId.toString());
      ConversationMessage request = ConversationMessage.fromJson(message);

      service
          .replyToMessage(request)
          .onSuccess(result -> ResponseBuilder.sendSuccess(ctx, result, urnGenerator))
          .onFailure(ctx::fail);
    } catch (Exception e) {
      LOGGER.error("Failed to reply to a conversation message", e);
      ctx.fail(e);
    }
  }

  private void handleUpdateMessage(RoutingContext ctx) {
    try {
      String requestType = ctx.pathParam("request_type");
      UUID messageId = UUID.fromString(ctx.pathParam("msg_id"));
      UUID userId = UUID.fromString(ctx.user().subject());
      ConversationUpdateRequest request =
          ctx.body().asJsonObject().mapTo(ConversationUpdateRequest.class);

      service
          .updateMessage(requestType, messageId, userId, request)
          .onSuccess(result -> ResponseBuilder.sendSuccess(ctx, result, urnGenerator))
          .onFailure(ctx::fail);
    } catch (Exception e) {
      LOGGER.error("Failed to update a conversation message", e);
      ctx.fail(e);
    }
  }

  private void handleDeleteMessage(RoutingContext ctx) {
    try {
      String requestType = ctx.pathParam("request_type");
      UUID messageId = UUID.fromString(ctx.pathParam("msg_id"));
      UUID userId = UUID.fromString(ctx.user().subject());

      service
          .deleteMessage(requestType, messageId, userId)
          .onSuccess(
              v -> ResponseBuilder.sendSuccess(ctx, "Conversation message deleted", urnGenerator))
          .onFailure(ctx::fail);
    } catch (Exception e) {
      LOGGER.error("Failed to delete a conversation message", e);
      ctx.fail(e);
    }
  }

  private void handleGetAllRequestIdAndType(RoutingContext ctx) {
    try {
      PaginatedRequest paginatedRequest =
          PaginationRequestBuilder.from(ctx)
              .allowedFiltersDbMap(REQUEST_TYPE_FILTER_MAP)
              .apiToDbMap(REQUEST_TYPE_FILTER_MAP)
              .allowedTimeFields(Set.of(CREATED_AT))
              .build();

      service
          .getByRequestType(paginatedRequest)
          .onSuccess(
              result ->
                  ResponseBuilder.sendSuccess(
                      ctx, result.data(), result.paginationInfo(), urnGenerator))
          .onFailure(ctx::fail);
    } catch (Exception e) {
      LOGGER.error("Failed to fetch conversation messages by request type", e);
      ctx.fail(e);
    }
  }
}
