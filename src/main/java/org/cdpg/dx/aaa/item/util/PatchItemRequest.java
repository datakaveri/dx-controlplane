package org.cdpg.dx.aaa.item.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;

public class PatchItemRequest {

    private final String itemId;
    private final String orgId;
    private final String userId;
    private final JsonObject requestBody;
  private  JsonArray allowedScopes;
  private List<String> allowedRoles;

  public PatchItemRequest(
      String itemId, String subId, String userId, JsonObject requestBody, JsonArray allowedScopes,
      List<String> allowedRoles) {
        this.itemId = itemId;
        this.orgId = subId;
      this.userId = userId;
      this.requestBody = requestBody;
      this.allowedScopes = allowedScopes;
      this.allowedRoles = allowedRoles;
    }

    public String getItemId() {
        return itemId;
    }

    public String getOrgId() {
        return orgId;
    }

    public JsonObject getRequestBody() {
        return requestBody;
    }

  public JsonArray getAllowedScopes() {
    return allowedScopes;
    }

  public List<String> getAllowedRoles() {
    return allowedRoles;
  }

  public String getUserId() {
        return userId;
    }
}


