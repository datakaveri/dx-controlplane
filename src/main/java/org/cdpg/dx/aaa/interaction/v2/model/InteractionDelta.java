package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonObject;

public record InteractionDelta(
    boolean oldLiked, boolean oldDisliked, boolean newLiked, boolean newDisliked) {

  public int likeDelta() {
    return (newLiked ? 1 : 0) - (oldLiked ? 1 : 0);
  }

  public int dislikeDelta() {
    return (newDisliked ? 1 : 0) - (oldDisliked ? 1 : 0);
  }

  public JsonObject toJson() {
    return new JsonObject()
        .put("oldLiked", oldLiked)
        .put("oldDisliked", oldDisliked)
        .put("newLiked", newLiked)
        .put("newDisliked", newDisliked)
        .put("likeDelta", likeDelta())
        .put("dislikeDelta", dislikeDelta());
  }
}
