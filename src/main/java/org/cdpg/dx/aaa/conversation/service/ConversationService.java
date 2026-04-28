package org.cdpg.dx.aaa.conversation.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.conversation.model.ConversationMessage;
import org.cdpg.dx.aaa.conversation.model.ConversationUpdateRequest;
import org.cdpg.dx.aaa.conversation.model.RequestTypeMapping;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.UpsertResult;

import java.util.UUID;

public interface ConversationService {

  Future<PaginatedResult<ConversationMessage>> getAllMessages(Float requestId, PaginatedRequest request);

  Future<ConversationMessage> getSingleMessage(int requestId, UUID messageId);

  Future<ConversationMessage>createMessage(
      ConversationMessage request);

  Future<ConversationMessage> replyToMessage(
    ConversationMessage request);

  Future<ConversationMessage> updateMessage(
      int requestId, UUID messageId, UUID userId, ConversationUpdateRequest request);

  Future<Void> deleteMessage(int requestId, UUID messageId, UUID userId);

  Future<PaginatedResult<RequestTypeMapping>> getByRequestType(PaginatedRequest request);
}
