package org.cdpg.dx.aaa.token.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import org.cdpg.dx.aaa.token.model.AccessTokenRequest;
import org.cdpg.dx.aaa.token.model.ConsentInfo;

public class AccessTokenRequestBuilder {

  public static AccessTokenRequest fromContext(RoutingContext ctx) {
    String clientId = ctx.request().getHeader("clientId");
    String clientSecret = ctx.request().getHeader("clientSecret");
    String delegationId = ctx.request().getHeader("delegationId"); // optional

    if (clientId == null || clientSecret == null) {
      throw new IllegalArgumentException("Missing required headers: clientId or clientSecret");
    }

    JsonObject body = null;
    String itemId = null;
    List<ConsentInfo> consentList = new ArrayList<>();

    if (ctx.body() != null && ctx.body().asJsonObject() != null) {
      body = ctx.body().asJsonObject();

      itemId = body.getString("itemId"); // optional

      JsonArray consentArray = body.getJsonArray("consentInfo");
      if (consentArray != null) {
        consentList =
            consentArray.stream()
                .filter(o -> o instanceof JsonObject)
                .map(
                    o -> {
                      JsonObject json = (JsonObject) o;
                      return new ConsentInfo(json.getString("ppbNo"), json.getString("purpose"));
                    })
                .toList();
      }
    }

    return new AccessTokenRequest(clientId, clientSecret, delegationId, itemId, consentList);
  }
}
