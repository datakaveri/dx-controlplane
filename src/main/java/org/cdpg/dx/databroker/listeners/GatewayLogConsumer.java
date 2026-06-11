package org.cdpg.dx.databroker.listeners;

import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.v2.model.GatewayAccessLogEntity;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

/**
 * Consumes gateway security events (published by dx-gateway-go to the
 * "gateway-logs" exchange) and persists them to gateway_access_log.
 *
 * <p>Same lifecycle/ack pattern as {@link AuditMessageConsumer}: manual ack,
 * one retry via NACK-requeue, then drop with an error log (gateway events are
 * a traffic ledger — losing one under sustained failure is preferable to
 * wedging the queue).
 */
public class GatewayLogConsumer implements RabitMqConsumer {

  private static final Logger LOGGER = LogManager.getLogger(GatewayLogConsumer.class);

  private final RabbitMQClient rabbitMqClient;
  private final String queueName;
  private final GatewayAccessLogDao dao;

  private RabbitMQConsumer consumer;

  private final QueueOptions queueOptions =
      new QueueOptions().setAutoAck(false).setMaxInternalQueueSize(100).setKeepMostRecent(true);

  public GatewayLogConsumer(
      RabbitMQClient rabbitMqClient, String queueName, PostgresService postgresService) {
    this.rabbitMqClient = rabbitMqClient;
    this.queueName = queueName;
    this.dao = new GatewayAccessLogDao(postgresService);
  }

  @Override
  public void start() {
    rabbitMqClient
        .start()
        .onSuccess(v -> consume())
        .onFailure(err -> LOGGER.fatal("RabbitMQ client start failed", err));
  }

  private void consume() {
    rabbitMqClient.basicConsumer(
        queueName,
        queueOptions,
        ar -> {
          if (ar.failed()) {
            LOGGER.error("Failed to create gateway-log consumer", ar.cause());
            return;
          }
          consumer = ar.result();
          consumer.handler(this::handleMessage);
          LOGGER.info("GatewayLogConsumer started on queue {}", queueName);
        });
  }

  private void handleMessage(RabbitMQMessage message) {
    long deliveryTag = message.envelope().getDeliveryTag();
    boolean redelivered = message.envelope().isRedeliver();

    JsonObject body = message.body().toJsonObject();

    GatewayAccessLogEntity entity;
    try {
      entity = GatewayAccessLogEntity.fromJson(body);
    } catch (Exception e) {
      LOGGER.error("Invalid gateway log message, acking and discarding: {}", body, e);
      rabbitMqClient.basicAck(deliveryTag, false);
      return;
    }

    dao.create(entity)
        .onSuccess(
            v -> {
              rabbitMqClient.basicAck(deliveryTag, false);
              LOGGER.debug("Gateway log persisted, event={}", entity.getEvent());
            })
        .onFailure(
            err -> {
              if (!redelivered) {
                LOGGER.warn("Gateway log insert failed, retrying once: {}", err.getMessage());
                rabbitMqClient.basicNack(deliveryTag, false, true);
              } else {
                LOGGER.error("Gateway log insert failed twice, dropping: {}", body, err);
                rabbitMqClient.basicAck(deliveryTag, false);
              }
            });
  }

  /** Minimal DAO — gateway_access_log is insert-only from this consumer. */
  private static final class GatewayAccessLogDao extends AbstractBaseDAO<GatewayAccessLogEntity> {
    GatewayAccessLogDao(PostgresService postgresService) {
      super(postgresService, "gateway_access_log", "id", GatewayAccessLogEntity::fromJson);
    }
  }
}
