package org.cdpg.dx.aaa.token.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public record ItemInfo(
  JsonArray type,
  String organizationId,
  String ownerUserId,
  String accessPolicy,
  String shortDescription,
  JsonArray resourceServer,
  JsonArray drl,
  String did,
  JsonObject constraints) {

  public static ItemInfo fromJson(JsonObject response) {

    if (response == null) {
      return new ItemInfo(null, null, null, null, null, new JsonArray(), new JsonArray(), null, new JsonObject());
    }

    JsonObject item = response;

//    Object typeObj = item.getValue("type");
//    String type = null;
//    if (typeObj instanceof JsonArray typeArray && !typeArray.isEmpty()) {
//      type = typeArray.getString(0);
//    } else if (typeObj instanceof String typeStr) {
//      type = typeStr;
//    }

    JsonArray type = item.getJsonArray("type", new JsonArray());
    String organizationId = item.getString("organizationId");
    String ownerUserId = item.getString("ownerUserId");
    String accessPolicy = item.getString("accessPolicy");
    String shortDescription = item.getString("shortDescription");
    JsonArray resourceServer = item.getJsonArray("resourceServer", new JsonArray());
    String did = item.getString("did");

    JsonArray drl=null;
    Object drlObj = item.getValue("drl");
    if (drlObj instanceof String) {
      try {
        drl = new JsonArray((String) drlObj);
      } catch (Exception e) {
        drl = new JsonArray().add(drlObj);
      }
    } else if (drlObj instanceof JsonArray) {
      drl = (JsonArray) drlObj;
    }

    JsonObject constraints = item.getJsonObject("cons", new JsonObject());

    return new ItemInfo(
      type,
      organizationId,
      ownerUserId,
      accessPolicy,
      shortDescription,
      resourceServer,
      drl,
      did,
      constraints);
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put("type", type)
      .put("organizationId", organizationId)
      .put("ownerUserId", ownerUserId)
      .put("accessPolicy", accessPolicy)
      .put("shortDescription", shortDescription)
      .put("resourceServer", resourceServer)
      .put("drl", drl)
      .put("did", did)
      .put("cons", constraints);
  }
}
