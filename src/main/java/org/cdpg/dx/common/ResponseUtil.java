package org.cdpg.dx.common;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.response.RestResponse;

public class ResponseUtil {
  public static JsonObject generateResponse(HttpStatusCode statusCode, ResponseUrn urn) {
    return generateResponse(statusCode, urn, statusCode.getDescription());
  }

  public static JsonObject generateResponse(
      HttpStatusCode statusCode, ResponseUrn urn, String message) {
    String type = urn.getUrn();

    return new RestResponse.Builder()
        .withMessage(message)
        .withType(type)
        .withTitle(statusCode.getDescription())
        .build()
        .toJson();
  }
}