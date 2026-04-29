package org.cdpg.dx.aaa.conversation.model;

import io.vertx.core.json.JsonObject;

public record ConversationUpdateRequest(String content, JsonObject metaData) {

  public static ConversationUpdateRequest fromJson(JsonObject json) {
    return new ConversationUpdateRequest(
      json.getJsonObject("content") != null ? json.getString("content") : null,
        json.getJsonObject("metadata") != null ? json.getJsonObject("metadata") : null);
  }
}
