package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonObject;

public record InteractionDelta(
    String entityId,
    String entityType,
    boolean oldLiked,
    boolean oldDisliked,
    boolean newLiked,
    boolean newDisliked,
    boolean oldBookmarked,
    boolean newBookmarked) {

  public int likeDelta() {
    return (newLiked ? 1 : 0) - (oldLiked ? 1 : 0);
  }

  public int dislikeDelta() {
    return (newDisliked ? 1 : 0) - (oldDisliked ? 1 : 0);
  }

  public int bookmarkDelta() {
    return (newBookmarked ? 1 : 0) - (oldBookmarked ? 1 : 0);
  }

  public JsonObject toJson() {
    return new JsonObject()
        .put("entityId", entityId)
        .put("entityType", entityType)
        .put("oldLiked", oldLiked)
        .put("oldDisliked", oldDisliked)
        .put("newLiked", newLiked)
        .put("newDisliked", newDisliked)
        .put("oldBookmarked", oldBookmarked)
        .put("newBookmarked", newBookmarked)
        .put("likeDelta", likeDelta())
        .put("dislikeDelta", dislikeDelta())
        .put("bookmarkDelta", bookmarkDelta());
  }
}
