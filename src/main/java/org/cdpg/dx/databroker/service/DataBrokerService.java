package org.cdpg.dx.databroker.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@VertxGen
@ProxyGen
public interface DataBrokerService {
  @GenIgnore
  static DataBrokerService createProxy(Vertx vertx, String address) {
    return new DataBrokerServiceVertxEBProxy(vertx, address);
  }

  Future<Void> publishMessageInternal(JsonObject body, String exchangeName, String routingKey);

  Future<String> publishMessageExternal(String exchangeName, String routingKey, JsonArray request);
}