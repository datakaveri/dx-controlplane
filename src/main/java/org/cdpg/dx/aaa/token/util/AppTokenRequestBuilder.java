package org.cdpg.dx.aaa.token.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.cdpg.dx.aaa.token.model.AppTokenRequest;

public final class AppTokenRequestBuilder {

  private AppTokenRequestBuilder() {
    // utility class
  }

  public static AppTokenRequest fromContext(RoutingContext ctx) {

    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      throw new IllegalArgumentException("Request body is required");
    }

    String appIdStr = body.getString("appId");
    String appSecret = body.getString("appSecret");

    if (appIdStr == null || appIdStr.isBlank()) {
      throw new IllegalArgumentException("Missing appId");
    }

    if (appSecret == null || appSecret.isBlank()) {
      throw new IllegalArgumentException("Missing appSecret");
    }

    UUID appId;
    try {
      appId = UUID.fromString(appIdStr);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid appId format");
    }

    return new AppTokenRequest(appId, appSecret.trim());
  }
}
