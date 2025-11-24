package org.cdpg.dx.aaa.subscription.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

public record GetSubscriptionModel(List<String> listString, String entities , JsonArray jsonArray) {
}
