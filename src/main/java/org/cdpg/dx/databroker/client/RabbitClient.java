package org.cdpg.dx.databroker.client;

import static org.cdpg.dx.common.ErrorCode.*;
import static org.cdpg.dx.common.ErrorMessage.INTERNAL_SERVER_ERROR;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.serviceproxy.ServiceException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RabbitClient {
  private static final Logger LOGGER = LogManager.getLogger(RabbitClient.class);
  private final RabbitMQClient iudxInternalRabbitMqClient;
  private final RabbitMQClient iudxRabbitMqClient;

  public RabbitClient(
      RabbitMQClient iudxInternalRabbitMqClient, RabbitMQClient iudxRabbitMqClient) {
    this.iudxRabbitMqClient = iudxRabbitMqClient;
    this.iudxInternalRabbitMqClient = iudxInternalRabbitMqClient;

    iudxInternalRabbitMqClient
        .start()
        .onSuccess(
            iudxInternalRabbitClientStart -> {
              LOGGER.info("RMQ client started for Internal Vhost");
            })
        .onFailure(
            iudxInternalRabbitClientStart -> {
              LOGGER.fatal("RMQ client startup failed");
            });
    iudxRabbitMqClient
        .start()
        .onSuccess(
            iudxRabbitClientStart -> {
              LOGGER.info("RMQ client started for Prod Vhost");
            })
        .onFailure(
            iudxRabbitClientStart -> {
              LOGGER.fatal("RMQ client startup failed");
            });
  }

  public Future<Void> publishMessageInternal(
      JsonObject body, String exchangeName, String routingKey) {
    Buffer buffer = Buffer.buffer(body.toString());
    Promise<Void> promise = Promise.promise();
    Future<Void> rabbitMqClientIudxInternalStartFuture;
    if (!iudxInternalRabbitMqClient.isConnected()) {
      rabbitMqClientIudxInternalStartFuture = iudxInternalRabbitMqClient.start();
    } else {
      rabbitMqClientIudxInternalStartFuture = Future.succeededFuture();
    }
    rabbitMqClientIudxInternalStartFuture
        .compose(
            started -> {
              return iudxInternalRabbitMqClient.basicPublish(exchangeName, routingKey, buffer);
            })
        .onSuccess(
            publishSuccess -> {
              promise.complete();
            })
        .onFailure(
            publishFailure -> {
              LOGGER.error("publishMessage failure " + publishFailure);
              //TODO: Update once exception is fix
              promise.fail(new ServiceException(ERROR_INTERNAL_SERVER, INTERNAL_SERVER_ERROR));
            });
    return promise.future();
  }

  public Future<Void> publishMessageExternal(
      String exchangeName, String routingKey, JsonArray request) {
    Promise<Void> promise = Promise.promise();
    Future<Void> rabbitMqClientIudxStartFuture;
    if (!iudxRabbitMqClient.isConnected()) {
      rabbitMqClientIudxStartFuture = iudxRabbitMqClient.start();
    } else {
      rabbitMqClientIudxStartFuture = Future.succeededFuture();
    }
    Buffer buffer = Buffer.buffer(request.encode());
    rabbitMqClientIudxStartFuture
        .compose(
            started -> {
              return iudxRabbitMqClient.basicPublish(exchangeName, routingKey, buffer);
            })
        .onSuccess(
            resultHandler -> {
              promise.complete();
            })
        .onFailure(
            failure -> {
              LOGGER.error("Fail : " + failure.getMessage());
              //TODO: Update once exception is fix
              promise.fail(new ServiceException(ERROR_INTERNAL_SERVER, INTERNAL_SERVER_ERROR));
            });
    return promise.future();
  }
}