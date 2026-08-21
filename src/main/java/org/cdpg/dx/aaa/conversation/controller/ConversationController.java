package org.cdpg.dx.aaa.conversation.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.*;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.CREATED_AT;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.conversation.model.ConversationMessage;
import org.cdpg.dx.aaa.conversation.model.ConversationUpdateRequest;
import org.cdpg.dx.aaa.conversation.service.ConversationParticipantService;
import org.cdpg.dx.aaa.conversation.service.ConversationService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;

public class ConversationController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(ConversationController.class);
  private static final Map<String, String> MESSAGE_FILTER_MAP =
      Map.of("requestType", "request_type","senderRole", "sender_role");
  private static final Map<String, String> REQUEST_TYPE_FILTER_MAP =
      Map.of(
          "type_key", "type_key",
          "display_name", "display_name",
          "is_active", "is_active");

  private final ConversationService service;
  private final ConversationParticipantService conversationParticipantService;
  private final EmailComposer emailComposer;
  private final URNGenerator urnGenerator;

  public ConversationController(
      ConversationService service,
      ConversationParticipantService conversationParticipantService,
      EmailComposer emailComposer,
      URNGenerator urnGenerator) {
    this.service = service;
    this.conversationParticipantService = conversationParticipantService;
    this.emailComposer = emailComposer;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {

    var selfAccess = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);
    var orgAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_USER_MANAGEMENT);
    var cosAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_MANAGEMENT);


    builder
        .operation(OP_GET_CONVERSATION_MESSAGE)
        .handler(this::handleGetSingleMessage);

    builder
        .operation(OP_CREATE_CONVERSATION_MESSAGE)
        .handler(this::handleCreateMessage);

    builder
        .operation(OP_REPLY_CONVERSATION_MESSAGE)
        .handler(this::handleReplyToMessage);

    builder
      .operation(OP_UPDATE_CONVERSATION_MESSAGE)
      .handler(this::handleUpdateMessage);

    builder
      .operation(OP_DELETE_CONVERSATION_MESSAGE)
      .handler(this::handleDeleteMessage);

    builder
      .operation(OP_GET_THREADED_MESSAGE)
      .handler(this::getThreadedMessages);
  }

  // New handler method:
  private void getThreadedMessages(RoutingContext ctx) {
    try {

      String requestId = ctx.pathParam("request_id");

      Map<String, Object> additionalFilters = new HashMap<>();
      additionalFilters.put("request_type_id", requestId);
      additionalFilters.put("parent_msg_id", null);


      PaginatedRequest paginatedRequest =
        PaginationRequestBuilder.from(ctx)
          .additionalFilters(additionalFilters)
          .allowedFiltersDbMap(MESSAGE_FILTER_MAP)
          .apiToDbMap(MESSAGE_FILTER_MAP)
          .allowedTimeFields(Set.of(CREATED_AT))
          .defaultSort("created_at", "asc")
          .build();

      service.getThreadedMessages(paginatedRequest)
        .onSuccess(result -> {
          JsonArray resultArray = new JsonArray();
          result.result().forEach(msg -> resultArray.add(msg.toJson()));  // ← explicit toJson()

          ResponseBuilder.sendSuccess(
            ctx,
            resultArray,              // ← JsonArray not List
            result.paginationInfo(),
            urnGenerator
          );
        })
        .onFailure(err -> {
          LOGGER.error("Failed to fetch threaded messages", err);
          ctx.fail(err);
        });

    } catch (Exception e) {
      LOGGER.error("Failed to fetch threaded messages", e);
      ctx.fail(e);
    }
  }


  private void handleGetSingleMessage(RoutingContext ctx) {
    try {
      String requestTypeIdStr = ctx.pathParam("request_id");
      UUID messageId = UUID.fromString(ctx.pathParam("msg_id"));

      service
          .getSingleMessage(requestTypeIdStr, messageId)
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
      String requestTypeId = ctx.pathParam("request_id");
      UUID userId = UUID.fromString(ctx.user().subject());

      JsonObject message = ctx.body().asJsonObject();
      message.put("request_type_id", requestTypeId);
      message.put("sender_id", userId.toString());

      ConversationMessage request = ConversationMessage.fromJson(message);

      service
          .createMessage(request)
          .onSuccess(
              result -> {
                sendConversationNotification(request);
                sendConversationAcknowledgement(request);

                ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
              })
          .onFailure(ctx::fail);
    } catch (Exception e) {
      LOGGER.error("Failed to create a conversation message", e);
      ctx.fail(e);
    }
  }

  private void sendConversationNotification(ConversationMessage message) {

    if (message.isInternal()) {
      return;
    }

    conversationParticipantService
        .getParticipants(message.requestType(), message.requestTypeId())
        .onSuccess(
            participants -> {
              if ("requester".equalsIgnoreCase(message.senderRole())) {

                emailComposer
                    .sendConversationMessageEmail(
                        participants.approver().email(),
                        participants.approver().givenName(),
                        message.content(),
                        "New Message on Your Request")
                    .onFailure(err -> LOGGER.error("Failed to send conversation email", err));

              } else if ("approver".equalsIgnoreCase(message.senderRole())) {

                emailComposer
                    .sendConversationMessageEmail(
                        participants.requester().email(),
                        participants.requester().givenName(),
                        message.content(),
                        "Update on Your Request")
                    .onFailure(err -> LOGGER.error("Failed to send conversation email", err));
              }
            })
        .onFailure(
            err ->
                LOGGER.error(
                    "Failed to resolve conversation participants for request {}",
                    message.requestTypeId(),
                    err));
  }

  private void sendConversationAcknowledgement(ConversationMessage message) {

    if (message.isInternal()) {
      return;
    }

    if (!"requester".equalsIgnoreCase(message.senderRole())) {
      return;
    }

    conversationParticipantService
        .getParticipants(message.requestType(), message.requestTypeId())
        .onSuccess(
            participants -> {
              emailComposer
                  .sendConversationAcknowledgementEmail(
                      participants.requester().email(),
                      participants.requester().givenName(),
                      message.content())
                  .onFailure(
                      err ->
                          LOGGER.error("Failed to send conversation acknowledgement email", err));
            })
        .onFailure(
            err ->
                LOGGER.error(
                    "Failed to resolve conversation participants for acknowledgement, request {}",
                    message.requestTypeId(),
                    err));
  }

  private void handleReplyToMessage(RoutingContext ctx) {
    try {
      String requestType = ctx.pathParam("request_id");
      UUID parentMsgId = UUID.fromString(ctx.pathParam("msg_id"));
      UUID userId = UUID.fromString(ctx.user().subject());

      JsonObject message = ctx.body().asJsonObject();
      message.put("request_type_id", requestType);
      message.put("sender_id", userId.toString());
      message.put("parent_msg_id", parentMsgId.toString());
      ConversationMessage request = ConversationMessage.fromJson(message);

      service
          .replyToMessage(request)
          .onSuccess(
              result -> {
                sendConversationNotification(request);
                sendConversationAcknowledgement(request);

                ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
              })
          .onFailure(ctx::fail);
    } catch (Exception e) {
      LOGGER.error("Failed to reply to a conversation message", e);
      ctx.fail(e);
    }
  }

  private void handleUpdateMessage(RoutingContext ctx) {
    try {
      String requestType = ctx.pathParam("request_id");
      UUID messageId = UUID.fromString(ctx.pathParam("msg_id"));
      UUID userId = UUID.fromString(ctx.user().subject());

      ConversationUpdateRequest request =
          ctx.body().asJsonObject().mapTo(ConversationUpdateRequest.class);

      service
          .updateMessage(requestType, messageId, userId, request)
          .onSuccess(
              result -> {
                sendConversationUpdateNotification(
                    requestType, UUID.fromString(ctx.pathParam("request_id")), request.content());

                ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
              })
          .onFailure(ctx::fail);

    } catch (Exception e) {
      LOGGER.error("Failed to update a conversation message", e);
      ctx.fail(e);
    }
  }

  private void sendConversationUpdateNotification(
      String requestType, UUID requestTypeId, String messageContent) {

    conversationParticipantService
        .getParticipants(requestType, requestTypeId)
        .onSuccess(
            participants -> {
              if (participants.requester() == null) {
                LOGGER.warn("Requester not found for conversation request {}", requestTypeId);
                return;
              }

              emailComposer
                  .sendConversationMessageEmail(
                      participants.requester().email(),
                      participants.requester().givenName(),
                      messageContent,
                      "Update on Your Request")
                  .onFailure(
                      err ->
                          LOGGER.error(
                              "Failed to send conversation update email for request {}",
                              requestTypeId,
                              err));
            })
        .onFailure(
            err ->
                LOGGER.error(
                    "Failed to resolve conversation participants for request {}",
                    requestTypeId,
                    err));
  }

  private void handleDeleteMessage(RoutingContext ctx) {
    try {
      String requestType = ctx.pathParam("request_id");
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
