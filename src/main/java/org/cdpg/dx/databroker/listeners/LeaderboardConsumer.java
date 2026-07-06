package org.cdpg.dx.databroker.listeners;

import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.leaderboard.enrichment.LeaderboardEnrichmentService;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;
import org.cdpg.dx.aaa.leaderboard.writer.LeaderboardWriterService;

import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.LOG_TYPE;

public class LeaderboardConsumer implements RabitMqConsumer {

  private static final Logger LOGGER = LogManager.getLogger(LeaderboardConsumer.class);

  private final RabbitMQClient rabbitMqClient;
  private final LeaderboardEnrichmentService enrichmentService;
  private final LeaderboardWriterService writerService;
  private final String queueName;
  private final QueueOptions queueOptions =
      new QueueOptions().setAutoAck(false).setMaxInternalQueueSize(100).setKeepMostRecent(true);
  private RabbitMQConsumer consumer;

  public LeaderboardConsumer(
      RabbitMQClient rabbitMqClient,
      LeaderboardEnrichmentService enrichmentService,
      LeaderboardWriterService writerService,
      String queueName) {

    this.rabbitMqClient = rabbitMqClient;
    this.enrichmentService = enrichmentService;
    this.writerService = writerService;
    this.queueName = queueName;
  }

  @Override
  public void start() {
    rabbitMqClient
        .start()
        .onSuccess(v -> consume())
        .onFailure(
            err ->
                LOGGER.fatal(
                    "Failed to start LeaderboardConsumer for queue:  {},", queueName, err));
  }

  private void consume() {
    rabbitMqClient.basicConsumer(
        queueName,
        queueOptions,
        ar -> {
          if (ar.failed()) {
            LOGGER.error("Failed to create RabbitMQ consumer", ar.cause());
            return;
          }

          consumer = ar.result();
          consumer.handler(this::handleMessage);

          LOGGER.info("AuditMessageConsumer started on queue {} ", queueName);
        });
  }

  private void handleMessage(RabbitMQMessage message) {

    JsonObject body;
    long deliveryTag = message.envelope().getDeliveryTag();

    try {
      body = message.body().toJsonObject();
      LOGGER.debug("Received Message: {}", body);
    } catch (Exception e) {
      LOGGER.error("Invalid JSON message, dropping: {}", message.body(), e);
      ack(deliveryTag); // poison → ACK & drop
      return;
    }

    String logType = body.getString(LOG_TYPE);
    if (!"asset".equalsIgnoreCase(logType)) {
      ack(deliveryTag);
      return;
    }

    LeaderboardEvent event;

    try {
      event = LeaderboardEvent.fromJson(body);
    } catch (Exception e) {
      e.getStackTrace();
      LOGGER.error("Invalid LeaderboardEvent payload, dropping: {}", body.encode(), e);
      ack(deliveryTag);
      return;
    }

    enrichmentService
        .enrich(event)
        .compose(writerService::handle) // MUST return Future<Void>
        .onSuccess(
            v -> {
              LOGGER.debug(
                  "Leaderboard event processed [assetId={}, action={}]",
                  event.assetId(),
                  event.action());
              ack(deliveryTag);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Leaderboard pipeline failed [assetId={}, action={}]",
                  event.assetId(),
                  event.action(),
                  err);
              nack(deliveryTag);
            });
  }

  private void ack(long deliveryTag) {
    rabbitMqClient
        .basicAck(deliveryTag, false)
        .onFailure(err -> LOGGER.error("Failed to ACK message {}", deliveryTag, err));
  }

  private void nack(long deliveryTag) {
    // requeue = true (retry)
    rabbitMqClient
        .basicNack(deliveryTag, false, false)
        .onFailure(err -> LOGGER.error("Failed to NACK message {}", deliveryTag, err));
  }
}
