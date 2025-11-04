package org.cdpg.dx.aaa.token.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.token.service.impl.TokenServiceImpl;

public record ItemInfo(
  String type,
  String organizationId,
  String ownerUserId,
  String accessPolicy,
  String shortDescription,
  JsonArray resourceServer,
  JsonArray dilr,
  String did,
  JsonObject constraints) {


  /** Factory method to create ItemInfo from raw JSON response */
  public static ItemInfo fromJson(JsonObject response) {

    System.out.println("Response is " + response);

    if (response == null) {
      return new ItemInfo(null, null, null, null, null, new JsonArray(), null, null, new JsonObject());
    }

    JsonObject item;

    // If response contains "results", unwrap the first result
    if (response.containsKey("results")) {
      JsonArray resultArray = response.getJsonArray("results");
      if (resultArray == null || resultArray.isEmpty()) {
        return new ItemInfo(null, null, null, null, null, new JsonArray(), null, null, new JsonObject());
      }
      item = resultArray.getJsonObject(0);
    } else {
      // Otherwise assume this is already the item JSON
      item = response;
    }


    Object typeObj = item.getValue("type");
    String type = null;
    if (typeObj instanceof JsonArray typeArray && !typeArray.isEmpty()) {
      type = typeArray.getString(0);
    } else if (typeObj instanceof String typeStr) {
      type = typeStr;
    }

    String organizationId = item.getString("organizationId");
    String ownerUserId = item.getString("ownerUserId");
    String accessPolicy = item.getString("accessPolicy");
    String shortDescription = item.getString("shortDescription");
    JsonArray resourceServer = item.getJsonArray("resourceServer", new JsonArray());
    JsonArray dilr = item.getJsonArray("drl",new JsonArray());
    String did = item.getString("did");
    JsonObject constraints = item.getJsonObject("constraints", new JsonObject());

    return new ItemInfo(
      type,
      organizationId,
      ownerUserId,
      accessPolicy,
      shortDescription,
      resourceServer,
      dilr,
      did,
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
      .put("drl", dilr)
      .put("did", did)
      .put("constraints", constraints);
  }
}
