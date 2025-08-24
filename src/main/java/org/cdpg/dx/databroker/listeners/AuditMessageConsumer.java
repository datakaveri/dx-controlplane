package org.cdpg.dx.databroker.listeners;

import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.activity.model.ActivityLog;
import org.cdpg.dx.aaa.activity.service.ActivityService;

public class AuditMessageConsumer implements RabitMqConsumer {

  private static final Logger LOGGER = LogManager.getLogger(AuditMessageConsumer.class);

  static int PG_COUNTER = 0;
  private final RabbitMQClient rabbitMqClient;
  private final ActivityService activityService;
  private final String queueName;
  private final QueueOptions options =
      new QueueOptions().setMaxInternalQueueSize(100).setKeepMostRecent(true).setAutoAck(false);

  public AuditMessageConsumer(
      RabbitMQClient rabbitMqClient, String queueName, ActivityService activityService) {
    this.rabbitMqClient = rabbitMqClient;
    this.queueName = queueName;
    this.activityService = activityService;
  }

  @Override
  public void start() {
    consume();
  }

  private void consume() {
    rabbitMqClient
        .start()
        .onSuccess(
            v ->
                rabbitMqClient.basicConsumer(
                    queueName,
                    options,
                    result -> {
                      if (result.succeeded()) {
                        RabbitMQConsumer mqConsumer = result.result();
                        mqConsumer.handler(this::handleMessage);
                      } else {
                        LOGGER.error(
                            "Failed to consume from {}: {}",
                            queueName,
                            result.cause().getMessage());
                      }
                    }))
        .onFailure(
            failure -> LOGGER.fatal("Rabbit client startup failed for {} Q consumer.", queueName));
  }

  private void handleMessage(RabbitMQMessage message) {
    LOGGER.info("Consuming message: {}", message.body());
    long deliveryTag = message.envelope().getDeliveryTag();
    JsonObject json = message.body().toJsonObject();
    ActivityLog activityLogEntity = ActivityLog.fromJson(json);
    LOGGER.debug("Activity log entity created: {}", activityLogEntity);

    activityService
        .insertActivityLogIntoDb(activityLogEntity)
        .onSuccess(
            v -> {
              LOGGER.info(
                  "Activity log inserted successfully for server {}",
                  activityLogEntity.originServer());
              rabbitMqClient.basicAck(deliveryTag, false); // Only ack on success
            })
        .onFailure(
            err -> {
              if (err.getMessage() != null && err.getMessage().contains("duplicate key")) {
                LOGGER.warn("Duplicate key error. Ignoring message.");
                rabbitMqClient.basicAck(deliveryTag, false); // Ack for duplicate key to avoid retry
              } else {
                LOGGER.error("Error inserting activity log: {}", err.getMessage());
                if (PG_COUNTER < 6) {
                  rabbitMqClient.basicNack(deliveryTag, false, true);
                  try {
                    Thread.sleep(5000);
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                  PG_COUNTER++;
                } else {
                  LOGGER.debug("sending message to dead letter queue");
                  rabbitMqClient.basicNack(deliveryTag, false, false);
                  PG_COUNTER = 0;
                }
              }
            });
  }
}
