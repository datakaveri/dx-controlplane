package org.cdpg.dx.aaa.conversation.model;

import io.vertx.core.json.JsonObject;

public record ConversationUpdateRequest(String content, JsonObject metaData, Boolean isInternal) {
  public static ConversationUpdateRequest fromJson(JsonObject json) {
    return new ConversationUpdateRequest(
      json.getString("content"),
      json.getJsonObject("metadata"),
      json.getBoolean("is_internal")
    );
  }
}
