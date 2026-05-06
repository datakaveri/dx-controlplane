package org.cdpg.dx.aaa.conversation.model;

import io.vertx.core.json.JsonObject;
import java.util.UUID;

public record ConversationCreateRequest(
    UUID parentMsgId,
    String requestType,
    String messageType,
    String content,
    Boolean internalNote,
    JsonObject metaData) {

  public static ConversationCreateRequest fromJson(JsonObject json) {
    return new ConversationCreateRequest(
        json.getString("parent_msg_id") != null
            ? UUID.fromString(json.getString("parent_msg_id"))
            : (json.getString("parentMsgId") != null
                ? UUID.fromString(json.getString("parentMsgId"))
                : null),
        json.getString("request_type"),
        json.getString("message_type"),
        json.getString("content"),
        json.getBoolean("internal_note"),
        json.getJsonObject("metadata") != null ? json.getJsonObject("metadata") :null);
  }
}
