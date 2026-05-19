package org.cdpg.dx.aaa.conversation.service.impl;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.conversation.dao.ConversationDao;
import org.cdpg.dx.aaa.conversation.dao.RequestTypeMappingDao;
import org.cdpg.dx.aaa.conversation.model.*;
import org.cdpg.dx.aaa.conversation.service.ConversationService;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.*;
import java.util.stream.Collectors;

public class ConversationServiceImpl implements ConversationService {

  private static final Logger LOGGER = LogManager.getLogger(ConversationServiceImpl.class);

  private final ConversationDao conversationDao;
  private final RequestTypeMappingDao requestTypeMappingDao;

  public ConversationServiceImpl(
    ConversationDao conversationDao,
    RequestTypeMappingDao requestTypeMappingDao) {
    this.conversationDao = conversationDao;
    this.requestTypeMappingDao = requestTypeMappingDao;
  }

  // -------------------------
  // LIST ROOT MESSAGES
  // -------------------------
  @Override
  public Future<PaginatedResult<ConversationMessage>> getAllMessages(
    String requestType, PaginatedRequest request) {

    return conversationDao.getAllWithFilters(request);
  }

  // -------------------------
  // SINGLE MESSAGE
  // -------------------------
  @Override
  public Future<ConversationMessage> getSingleMessage(
    String requestType, UUID messageId) {

    return conversationDao.getAllWithFilters(
        Map.of("request_type_id", requestType, "id", messageId.toString()))
      .compose(messages -> {
        List<ConversationMessage> list = safeCast(messages);

        return list.isEmpty()
          ? Future.failedFuture(new DxNotFoundException("Conversation message not found"))
          : Future.succeededFuture(list.getFirst());
      });
  }

  // -------------------------
  // CREATE MESSAGE
  // -------------------------
  @Override
  public Future<ConversationMessage> createMessage(ConversationMessage request) {

    LOGGER.info("request in json: {}", request.toJson());

    ConversationMessage message = new ConversationMessage(
      null,
      request.requestType(),
      request.requestTypeId(),
      request.parentMsgId(),
      request.senderId(),
      request.senderRole(),
      request.messageType(),
      request.content(),
      request.isInternal(),
      request.metaData(),
      null,
      0,
      false,
      null
    );

    return conversationDao.create(message);
  }

  // -------------------------
  // REPLY
  // -------------------------
  @Override
  public Future<ConversationMessage> replyToMessage(ConversationMessage request) {

    return conversationDao.create(request)
      .compose(created ->
        conversationDao.incrementReplyCount(request.parentMsgId())
          .map(v -> created)
      );
    }


  // -------------------------
  // UPDATE
  // -------------------------
  @Override
  public Future<ConversationMessage> updateMessage(
    String requestType,
    UUID messageId,
    UUID userId,
    ConversationUpdateRequest request) {

    Map<String, Object> condition = Map.of(
      "id", messageId.toString(),
      "request_type_id", requestType,
      "sender_id", userId.toString()
    );

    Map<String, Object> updates = new HashMap<>();
    if (request.content() != null) updates.put("content", request.content());
    if (request.metaData() != null) updates.put("metadata", request.metaData());
    if (request.isInternal() != null) updates.put("is_internal", request.isInternal());

    return conversationDao.update(condition, updates)
      .compose(v -> conversationDao.getAllWithFilters(Map.of("id", messageId.toString())))
      .compose(messages -> {
        if (messages.isEmpty()) {
          return Future.failedFuture(new DxNotFoundException("Conversation message not found"));
        }
        return Future.succeededFuture(messages.getFirst());
      });
  }
  // -------------------------
  // DELETE
  // -------------------------
  @Override
  public Future<Void> deleteMessage(String requestType, UUID messageId, UUID userId) {
    return conversationDao.getAllWithFilters(
        Map.of(
          "id", messageId.toString(),
          "request_type_id", requestType,
          "sender_id", userId.toString()
        ))
      .compose(messages -> {
        if (messages.isEmpty()) {
          return Future.failedFuture(new DxNotFoundException("Conversation message not found"));
        }
        ConversationMessage message = messages.getFirst();

        // Delete the message and all its descendants recursively
        return deleteRecursively(messageId)
          .compose(v -> {
            // If it's a reply, decrement parent's reply count (don't delete parent)
            if (message.parentMsgId() != null) {
              return conversationDao.decrementReplyCount(message.parentMsgId());
            }
            return Future.succeededFuture();
          })
          .mapEmpty();
      });
  }

  /**
   * Recursively deletes a message and all its descendants (replies to replies, etc.)
   */
  private Future<Void> deleteRecursively(UUID messageId) {
    return conversationDao.getAllWithFilters(Map.of("parent_msg_id", messageId.toString()))
      .compose(replies -> {
        // Recursively delete each reply and its children first
        List<Future<Void>> childDeletions = replies.stream()
          .map(reply -> deleteRecursively(reply.id()))
          .toList();

        return Future.all(childDeletions)
          .compose(v -> conversationDao.delete(messageId))
          .mapEmpty();
      });
  }

  // -------------------------
  // REQUEST TYPE MAPPING
  // -------------------------
  @Override
  public Future<PaginatedResult<RequestTypeMapping>> getByRequestType(PaginatedRequest request) {
    return requestTypeMappingDao.getAllWithFilters(request);
  }

  // -------------------------
  // THREADED MESSAGES
  // -------------------------
  @Override
  public Future<ThreadedResult> getThreadedMessages(PaginatedRequest request) {

    return conversationDao.getAllWithFilters(request)
      .compose(rootResult -> {

        List<ConversationMessage> roots = safeCast(rootResult.data());

        if (roots.isEmpty()) {
          return Future.succeededFuture(
            new ThreadedResult(List.of(), rootResult.paginationInfo()));
        }

        List<UUID> rootIds = roots.stream()
          .map(ConversationMessage::id)
          .toList();

        return conversationDao.getRepliesByParentIds(rootIds, 3)
          .compose(depth1Raw -> {

            List<ConversationMessage> depth1 = safeCast(depth1Raw);

            if (depth1.isEmpty()) {
              List<ConversationMessage> enrichedRoots = roots.stream()
                .map(root -> root.withReplies(
                  buildThreadedReplies(List.of(), root.replyCount(), 3)
                ))
                .toList();

              return Future.succeededFuture(
                new ThreadedResult(enrichedRoots, rootResult.paginationInfo()));
            }

            List<UUID> depth1Ids = depth1.stream()
              .map(ConversationMessage::id)
              .toList();

            return conversationDao.getRepliesByParentIds(depth1Ids, 3)
              .map(depth2Raw -> {

                List<ConversationMessage> depth2 = safeCast(depth2Raw);

                Map<UUID, List<ConversationMessage>> depth2ByParent =
                  depth2.stream()
                    .collect(Collectors.groupingBy(ConversationMessage::parentMsgId));

                List<ConversationMessage> enrichedDepth1 = depth1.stream()
                  .map(d1 -> d1.withReplies(
                    buildThreadedReplies(
                      depth2ByParent.getOrDefault(d1.id(), List.of()),
                      d1.replyCount(),
                      3
                    )
                  ))
                  .toList();

                Map<UUID, List<ConversationMessage>> depth1ByParent =
                  enrichedDepth1.stream()
                    .collect(Collectors.groupingBy(ConversationMessage::parentMsgId));

                List<ConversationMessage> threaded = roots.stream()
                  .map(root -> root.withReplies(
                    buildThreadedReplies(
                      depth1ByParent.getOrDefault(root.id(), List.of()),
                      root.replyCount(),
                      3
                    )
                  ))
                  .toList();

                return new ThreadedResult(threaded, rootResult.paginationInfo());
              });
          });
      });
  }

  // -------------------------
  // SAFE CAST HELPER (IMPORTANT)
  // -------------------------
  @SuppressWarnings("unchecked")
  private List<ConversationMessage> safeCast(Object obj) {
    return (List<ConversationMessage>) (List<?>) obj;
  }

  // -------------------------
  // THREAD META BUILDER
  // -------------------------
  private ThreadedReplies buildThreadedReplies(
    List<ConversationMessage> replies,
    int totalCount,
    int pageSize) {

    int totalPages = (int) Math.ceil((double) totalCount / pageSize);

    PaginationInfo info = new PaginationInfo(
      1,
      pageSize,
      totalCount,
      totalPages,
      totalCount > pageSize,
      false
    );

    return new ThreadedReplies(replies, info);
  }


}
