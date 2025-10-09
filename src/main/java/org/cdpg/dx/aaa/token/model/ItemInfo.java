package org.cdpg.dx.aaa.token.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public record ItemInfo(
    String type,
    String organizationId,
    String ownerUserId,
    String accessPolicy,
    String shortDescription,
    JsonArray resourceServer,
    JsonObject constraints) {

  /** Factory method to create ItemInfo from raw JSON response */
  public static ItemInfo fromJson(JsonObject response) {
    if (response == null || !response.containsKey("result")) {
      return new ItemInfo(null, null, null, null, null, new JsonArray(), new JsonObject());
    }

    JsonArray resultArray = response.getJsonArray("result");
    if (resultArray == null || resultArray.isEmpty()) {
      return new ItemInfo(null, null, null, null, null, new JsonArray(), new JsonObject());
    }

    JsonObject item = resultArray.getJsonObject(0);

    // Extract type (array or string)
    Object typeObj = item.getValue("type");
    String type = null;
    if (typeObj instanceof JsonArray typeArray && !typeArray.isEmpty()) {
      type = typeArray.getString(0);
    } else if (typeObj instanceof String typeStr) {
      type = typeStr;
    }

    // Extract other fields
    String organizationId = item.getString("organizationId");
    String ownerUserId = item.getString("ownerUserId");
    String accessPolicy = item.getString("accessPolicy");
    String shortDescription = item.getString("shortDescription");
    JsonArray resourceServer = item.getJsonArray("resourceServer", new JsonArray());
    JsonObject constraints = item.getJsonObject("constraints", new JsonObject());

    return new ItemInfo(
        type,
        organizationId,
        ownerUserId,
        accessPolicy,
        shortDescription,
        resourceServer,
        constraints);
  }

  /** Convert back to JsonObject (for JWT claims, etc.) */
  public JsonObject toJson() {
    return new JsonObject()
        .put("type", type)
        .put("organizationId", organizationId)
        .put("ownerUserId", ownerUserId)
        .put("accessPolicy", accessPolicy)
        .put("shortDescription", shortDescription)
        .put("resourceServer", resourceServer)
        .put("constraints", constraints);
  }
}
