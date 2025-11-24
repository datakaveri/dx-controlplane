package org.cdpg.dx.aaa.subscription.model;

import io.vertx.core.json.JsonObject;

public record RegisterSubscription(String subscriptionId, String userId, String apiKey, String queueName, String url, int port, String vHost) {
    public JsonObject toJson() {
        return new JsonObject()
                .put("id", subscriptionId)
                .put("userId", userId)
                .put("apiKey", apiKey)
                .put("queueName", queueName)
                .put("url", url)
                .put("port", port)
                .put("vHost", vHost);
    }
}
