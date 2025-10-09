package org.cdpg.dx.aaa.item.util;

import io.vertx.core.json.JsonObject;
import java.util.List;

public class PatchItemRequest {

    private final String itemId;
    private final String orgId;
    private final String userId;
    private final JsonObject requestBody;
    private final List<String> allowedRoles;
    public PatchItemRequest(String itemId, String subId, String userId, JsonObject requestBody,
                            List<String> allowedRoles) {
        this.itemId = itemId;
        this.orgId = subId;
      this.userId = userId;
      this.requestBody = requestBody;
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

    public List<String> getAllowedRoles() {
        return allowedRoles;
    }

    public String getUserId() {
        return userId;
    }
}


