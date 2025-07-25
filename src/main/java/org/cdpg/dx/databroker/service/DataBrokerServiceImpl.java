package org.cdpg.dx.databroker.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.databroker.client.RabbitClient;

public class DataBrokerServiceImpl implements DataBrokerService {
  private static final Logger LOGGER = LogManager.getLogger(DataBrokerServiceImpl.class);
  private final RabbitClient rabbitClient;

  public DataBrokerServiceImpl(
      RabbitClient client) {
    this.rabbitClient = client;
    LOGGER.trace("Info : DataBrokerServiceImpl#constructor() completed");
  }

  @Override
  public Future<String> publishMessageExternal(
      String exchangeName, String routingKey, JsonArray request) {
    Promise<String> promise = Promise.promise();
    rabbitClient
        .publishMessageExternal(exchangeName, routingKey, request)
        .onSuccess(
            resultHandler -> {
              LOGGER.info("Success : Message published to queue");
              promise.complete("success");
            })
        .onFailure(
            failure -> {
              LOGGER.error("Fail : " + failure.getMessage());
              promise.fail(failure);
            });
    return promise.future();
  }

  @Override
  public Future<Void> publishMessageInternal(
      JsonObject body, String exchangeName, String routingKey) {
    Promise<Void> promise = Promise.promise();
    rabbitClient
        .publishMessageInternal(body, exchangeName, routingKey)
        .onSuccess(
            publishSuccess -> {
              LOGGER.debug("publishMessage success");
              promise.complete();
            })
        .onFailure(
            publishFailure -> {
              LOGGER.debug("publishMessage failure");
              promise.fail(publishFailure);
            });
    return promise.future();
  }
}