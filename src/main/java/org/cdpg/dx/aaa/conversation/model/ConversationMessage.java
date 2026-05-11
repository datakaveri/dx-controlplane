package org.cdpg.dx.aaa.conversation.model;

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
  UUID parentMsgId,
  UUID senderId,
  String senderRole,
  String messageType,
  String content,
  Boolean isInternal,
  JsonObject metaData,
  OffsetDateTime createdAt)
  implements BaseEntity<ConversationMessage> {

  public static ConversationMessage fromJson(JsonObject json) {
    return new ConversationMessage(
      json.getString("id") != null ? UUID.fromString(json.getString("id")) : null,
      json.getString("request_type"),
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
      json.getString(Constants.CREATED_AT) != null                          // ✅ handles "...Z" format
        ? OffsetDateTime.parse(json.getString(Constants.CREATED_AT),
        DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        : null);
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (id != null) map.put("id", id.toString());
    if (requestType != null) map.put("request_type", requestType);    // ✅ int
    if (parentMsgId != null) map.put("parent_msg_id", parentMsgId.toString());
    if (senderId != null) map.put("sender_id", senderId.toString());
    if (senderRole != null) map.put("sender_role", senderRole);
    if (messageType != null) map.put("message_type", messageType);
    if (content != null) map.put("content", content);
    if (isInternal != null) map.put("is_internal", isInternal);
    if (metaData != null) map.put("metadata", metaData);
    if (createdAt != null) map.put(Constants.CREATED_AT,
      createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));            // ✅ String for event bus
    return map;
  }

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
    if (createdAt != null) json.put(Constants.CREATED_AT,
      createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    return json;
  }

  @Override
  public String getTableName() {
    return "";                                                // ✅ was empty string
  }
}
