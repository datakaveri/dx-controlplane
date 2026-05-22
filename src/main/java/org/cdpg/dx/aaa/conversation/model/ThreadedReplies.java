package org.cdpg.dx.aaa.conversation.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.util.PaginationInfo;

import java.util.List;

public record ThreadedReplies(
  List<ConversationMessage> result,
  PaginationInfo paginationInfo
) {
  public JsonObject toJson() {
    JsonArray resultArray = new JsonArray();
    if (result != null) {
      result.forEach(msg -> resultArray.add(msg.toJson()));  // ← explicit toJson()
    }

    JsonObject paginationJson = new JsonObject()
      .put("page",        paginationInfo.getPage())
      .put("size",        paginationInfo.getSize())
      .put("totalCount",  paginationInfo.getTotalCount())
      .put("totalPages",  paginationInfo.getTotalPages())
      .put("hasNext",     paginationInfo.isHasNext())
      .put("hasPrevious", paginationInfo.isHasPrevious());

    return new JsonObject()
      .put("result", resultArray)
      .put("paginationInfo", paginationJson);
  }
}
