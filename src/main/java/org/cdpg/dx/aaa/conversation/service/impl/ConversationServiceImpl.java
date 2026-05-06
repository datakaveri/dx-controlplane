package org.cdpg.dx.aaa.conversation.service.impl;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.conversation.dao.ConversationDao;
import org.cdpg.dx.aaa.conversation.dao.RequestTypeMappingDao;
import org.cdpg.dx.aaa.conversation.model.ConversationMessage;
import org.cdpg.dx.aaa.conversation.model.ConversationUpdateRequest;
import org.cdpg.dx.aaa.conversation.model.RequestTypeMapping;
import org.cdpg.dx.aaa.conversation.service.ConversationService;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.UpsertResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConversationServiceImpl implements ConversationService {

  private static final Logger LOGGER = LogManager.getLogger(ConversationServiceImpl.class);
  private final ConversationDao conversationDao;
  private final RequestTypeMappingDao requestTypeMappingDao;

  public ConversationServiceImpl(
      ConversationDao conversationDao, RequestTypeMappingDao requestTypeMappingDao) {
    this.conversationDao = conversationDao;
    this.requestTypeMappingDao = requestTypeMappingDao;
  }

  @Override
  public Future<PaginatedResult<ConversationMessage>> getAllMessages(
      String requestType, PaginatedRequest request) {
    return conversationDao
        .getAllWithFilters(request);
  }

  @Override
  public Future<ConversationMessage> getSingleMessage(String requestType, UUID messageId) {
    return conversationDao
        .getAllWithFilters(
            Map.of("request_type", requestType, "id", messageId.toString()))
        .compose(
            messages ->
                messages.isEmpty()
                    ? Future.failedFuture(new DxNotFoundException("Conversation message not found"))
                    : Future.succeededFuture(messages.getFirst()));
  }

  @Override
  public Future<ConversationMessage> createMessage(
      ConversationMessage request) {

    LOGGER.info("request in json:{}",request.toJson());
    ConversationMessage message =
        new ConversationMessage(
            null,
            request.requestType(),
            request.parentMsgId(),
            request.senderId(),
            request.senderRole(),
            request.messageType(),
            request.content(),
            request.isInternal(),
            request.metaData(),
            null);
    return conversationDao.create(message);
  }

  @Override
  public Future<ConversationMessage> replyToMessage(
    ConversationMessage request) {
    ConversationMessage reply =
      new ConversationMessage(
        null,
        request.requestType(),
        request.parentMsgId(),
        request.senderId(),
        request.senderRole(),
        request.messageType(),
        request.content(),
        request.isInternal(),
        request.metaData(),
        null);
    return conversationDao.create(reply);
  }

  @Override
  public Future<ConversationMessage> updateMessage(
      String requestType, UUID messageId, UUID userId, ConversationUpdateRequest request) {
    Map<String, Object> condition =
        Map.of(
            "id", messageId.toString(),
            "request_type", requestType,
            "sender_id", userId.toString());

    Map<String, Object> updates = new HashMap<>();
    if (request.content() != null) updates.put("content", request.content());
    if (request.metaData() != null) updates.put("metadata", request.metaData());
    return conversationDao.update(
        condition,updates);
  }

  @Override
  public Future<Void> deleteMessage(String requestType, UUID messageId, UUID userId) {
    return conversationDao
        .getAllWithFilters(
            Map.of(
                "id", messageId.toString(),
                "request_type", requestType,
                "sender_id", userId.toString()))
        .compose(
            messages -> {
              if (messages.isEmpty()) {
                return Future.failedFuture(new DxNotFoundException("Conversation message not found"));
              }
              return conversationDao.delete(messageId).mapEmpty();
            });
  }

  @Override
  public Future<PaginatedResult<RequestTypeMapping>> getByRequestType(PaginatedRequest request) {
    return requestTypeMappingDao
        .getAllWithFilters(request);
  }
}
