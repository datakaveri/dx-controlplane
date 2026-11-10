package org.cdpg.dx.aaa.conversation.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record ConversationMessage(
  UUID id,
  String requestType,
  UUID requestTypeId,
  UUID parentMsgId,
  UUID senderId,
  String senderRole,
  String messageType,
  String content,
  Boolean isInternal,
  JsonObject metaData,
  OffsetDateTime createdAt,

  int replyCount,          // DB column: reply_count
  boolean hasReplies,      // derived: replyCount > 0
  ThreadedReplies replies  // NOT DB column (service-layer only)
)
  implements BaseEntity<ConversationMessage> {

  // -------------------------
  // JSON → Model
  // -------------------------
  public static ConversationMessage fromJson(JsonObject json) {
    int replyCount = json.getInteger("reply_count", 0);
    boolean hasReplies = replyCount > 0;

    return new ConversationMessage(
      json.getString("id") != null ? UUID.fromString(json.getString("id")) : null,
      json.getString("request_type"),
      json.getString("request_type_id") != null               // ← ADD THIS
        ? UUID.fromString(json.getString("request_type_id"))
        : null,
      json.getString("parent_msg_id") != null
        ? UUID.fromString(json.getString("parent_msg_id"))
        : null,
      json.getString("sender_id") != null
        ? UUID.fromString(json.getString("sender_id"))
        : null,
      json.getString("sender_role"),
      json.getString("message_type"),
      json.getString("content"),
      json.getBoolean("is_internal"),
      json.getJsonObject("metadata") != null ? json.getJsonObject("metadata") : null,
      json.getString(Constants.CREATED_AT) != null
        ? OffsetDateTime.parse(json.getString(Constants.CREATED_AT), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        : null,
      replyCount,
      hasReplies,
      null
    );
  }

  // -------------------------
  // Builders (immutable updates)
  // -------------------------
  public ConversationMessage withHasReplies(boolean hasReplies) {
    return new ConversationMessage(
      id, requestType, requestTypeId, parentMsgId, senderId, senderRole,
      messageType, content, isInternal, metaData, createdAt,
      replyCount, hasReplies, replies
    );
  }

  public ConversationMessage withReplyCount(int replyCount) {
    return new ConversationMessage(
      id, requestType, requestTypeId,parentMsgId, senderId, senderRole,
      messageType, content, isInternal, metaData, createdAt,
      replyCount, hasReplies, replies
    );
  }

  public ConversationMessage withReplies(ThreadedReplies replies) {
    return new ConversationMessage(
      id, requestType,requestTypeId, parentMsgId, senderId, senderRole,
      messageType, content, isInternal, metaData, createdAt,
      replyCount, hasReplies, replies
    );
  }

  // -------------------------
  // DB insert/update mapping
  // -------------------------
  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put("id", id.toString());
    if (requestType != null) map.put("request_type", requestType);
    if (parentMsgId != null) map.put("parent_msg_id", parentMsgId.toString());
    if (senderId != null) map.put("sender_id", senderId.toString());
    if (senderRole != null) map.put("sender_role", senderRole);
    if (messageType != null) map.put("message_type", messageType);
    if (content != null) map.put("content", content);
    if (isInternal != null) map.put("is_internal", isInternal);
    if (metaData != null) map.put("metadata", metaData);
    if (requestTypeId != null) map.put("request_type_id", requestTypeId.toString());

    if (createdAt != null) {
      map.put(
        Constants.CREATED_AT,
        createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
      );
    }

    // ⚠️ intentionally NOT mapping:
    // replyCount, hasReplies, replies
    // because they are derived / DB-managed / service-layer only

    return map;
  }

  // -------------------------
  // JSON output (API response)
  // -------------------------
  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put("id", id.toString());
    if (requestType != null) json.put("request_type", requestType);
    if (parentMsgId != null) json.put("parent_msg_id", parentMsgId.toString());
    if (senderId != null) json.put("sender_id", senderId.toString());
    if (senderRole != null) json.put("sender_role", senderRole);
    if (messageType != null) json.put("message_type", messageType);
    if (content != null) json.put("content", content);
    if (isInternal != null) json.put("is_internal", isInternal);
    if (metaData != null) json.put("metadata", metaData);
    if (requestTypeId != null) json.put("request_type_id", requestTypeId.toString());


    if (createdAt != null) {
      json.put(
        Constants.CREATED_AT,
        createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
      );
    }

    if (replyCount > 0 && replies != null) {
      json.put("replies", replies.toJson());  // ← clean, uses ThreadedReplies.toJson()
    } else {
      json.put("replies", new JsonArray());
    }

    json.put("reply_count", replyCount);
    json.put("hasReplies", hasReplies);


    return json;
  }

  @Override
  public String getTableName() {
    return "";                                                // ✅ was empty string
  }
}
