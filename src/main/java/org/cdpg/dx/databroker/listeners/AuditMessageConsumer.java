package org.cdpg.dx.databroker.listeners;

import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.v2.enrichment.AssetEnrichmentService;
import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
import org.cdpg.dx.auditing.v2.service.UserActivityAuditLogService;

public class AuditMessageConsumer implements RabitMqConsumer {

  private static final Logger LOGGER = LogManager.getLogger(AuditMessageConsumer.class);

  private final RabbitMQClient rabbitMqClient;
  private final String queueName;
  private final AssetEnrichmentService assetEnrichmentService;
  private final UserActivityAuditLogService auditService;
  private final boolean dlqEnabled;

  private RabbitMQConsumer consumer;

  private final QueueOptions queueOptions =
      new QueueOptions().setAutoAck(false).setMaxInternalQueueSize(100).setKeepMostRecent(true);

  public AuditMessageConsumer(
      RabbitMQClient rabbitMqClient,
      String queueName,
      AssetEnrichmentService assetEnrichmentService,
      UserActivityAuditLogService auditService,
      boolean dlqEnabled) {

    this.rabbitMqClient = rabbitMqClient;
    this.queueName = queueName;
    this.assetEnrichmentService = assetEnrichmentService;
    this.auditService = auditService;
    this.dlqEnabled = dlqEnabled;
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
            LOGGER.error("Failed to create RabbitMQ consumer", ar.cause());
            return;
          }

          consumer = ar.result();
          consumer.handler(this::handleMessage);

          LOGGER.info(
              "AuditMessageConsumer started on queue {} (dlqEnabled={})", queueName, dlqEnabled);
        });
  }

  // ----------------------------------------------------
  // Message handling
  // ----------------------------------------------------
  private void handleMessage(RabbitMQMessage message) {
    boolean redelivered = message.envelope().isRedeliver();

    LOGGER.info(
        "Audit message received [deliveryTag={}, redelivered={}]",
        message.envelope().getDeliveryTag(),
        redelivered);

    long deliveryTag = message.envelope().getDeliveryTag();
    JsonObject body = message.body().toJsonObject();

    ActivityAuditLogEntity entity;
    try {
      entity = ActivityAuditLogEntity.fromJson(body);
    } catch (Exception e) {
      LOGGER.error("Invalid audit message, acking and discarding: {}", body, e);
      rabbitMqClient.basicAck(deliveryTag, false);
      return;
    }

    assetEnrichmentService
        .enrich(entity)
        .compose(auditService::insertUserActivityLogIntoDb)
        .onSuccess(
            v -> {
              rabbitMqClient.basicAck(deliveryTag, false);
              LOGGER.debug("Audit log persisted successfully, id={}", entity.getId());
            })
        .onFailure(err -> handleFailure(err, message));
  }

  // ----------------------------------------------------
  // Failure handling (ONLY ONE RETRY)
  // ----------------------------------------------------
  private void handleFailure(Throwable err, RabbitMQMessage message) {

    long deliveryTag = message.envelope().getDeliveryTag();

    // 1️⃣ First failure → retry once
    if (!message.envelope().isRedeliver()) {
      LOGGER.warn("Audit insert failed (first attempt), retrying once", err);
      rabbitMqClient.basicNack(deliveryTag, false, true);
      return;
    }

    // 2️⃣ Second failure → DLQ or drop
    if (dlqEnabled) {
      LOGGER.error("Audit insert failed (second attempt), sending to DLQ", err);
      rabbitMqClient.basicNack(deliveryTag, false, false);
    } else {
      LOGGER.error("Audit insert failed (second attempt), dropping message", err);
      rabbitMqClient.basicAck(deliveryTag, false);
    }
  }
}
