package org.cdpg.dx.aaa.subscription.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public record GetAllSubscription(JsonArray result, int count) {
  public JsonArray withoutTotalCount() {
    JsonArray cleaned = new JsonArray();

    for (int i = 0; i < result.size(); i++) {
      JsonObject obj = result.getJsonObject(i).copy();
      obj.remove("total_result_count");
      cleaned.add(obj);
    }

    return cleaned;
  }
}
