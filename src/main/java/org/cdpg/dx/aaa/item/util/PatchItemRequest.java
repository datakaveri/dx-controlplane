package org.cdpg.dx.aaa.item.util;

import io.vertx.core.json.JsonObject;

public class PatchItemRequest {

    private final String itemId;
    private final String subId;
    private final JsonObject requestBody;
    public PatchItemRequest(String itemId, String subId, JsonObject requestBody) {
        this.itemId = itemId;
        this.subId = subId;
        this.requestBody = requestBody;
    }

    public String getItemId() {
        return itemId;
    }

    public String getSubId() {
        return subId;
    }

    public JsonObject getRequestBody() {
        return requestBody;
    }
}


