package org.cdpg.dx.databroker.listeners;

import static org.cdpg.dx.email.util.Constants.EMAIL_PERMANENT_FAILURE_CODE;

import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import io.vertx.serviceproxy.ServiceException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.email.service.EmailService;

public class EmailMessageConsumer implements RabitMqConsumer {
  static final int MAX_RETRIES = 6;
  static final long RETRY_DELAY_MS = 5000;
  private static final Logger LOGGER = LogManager.getLogger(EmailMessageConsumer.class);
  private static final int PREFETCH_COUNT = 10;
  private final Vertx vertx;
  private final RabbitMQClient rabbitMQClient;
  private final String queueName;
  private final QueueOptions options =
      new QueueOptions().setMaxInternalQueueSize(100).setKeepMostRecent(true).setAutoAck(false);
  private final EmailService emailService;

  public EmailMessageConsumer(
      Vertx vertx, RabbitMQClient rabbitMQClient, String queueName, EmailService emailService) {
    this.vertx = vertx;
    this.rabbitMQClient = rabbitMQClient;
    this.queueName = queueName;
    this.emailService = emailService;
  }

  @Override
  public void start() {
    LOGGER.trace("EmailMessageConsumer started for queue: {}", queueName);
    consume();
  }

  private void consume() {
    rabbitMQClient
        .start()
        // Messages stay unacked while they wait to be retried, so this also caps how many
        // sends are in progress during an SMTP outage.
        .compose(v -> rabbitMQClient.basicQos(PREFETCH_COUNT))
        .onSuccess(
            v ->
                rabbitMQClient.basicConsumer(
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
    LOGGER.info("Consuming email message: {}", message.body());
    long deliveryTag = message.envelope().getDeliveryTag();
    JsonObject json;
    try {
      json = message.body().toJsonObject();
    } catch (DecodeException e) {
      LOGGER.error(
          "Email message with deliveryTag: {} is not valid JSON. Error: {}",
          deliveryTag,
          e.getMessage());
      deadLetter(deliveryTag);
      return;
    }
    send(deliveryTag, json, 0);
  }

  /**
   * The delivery stays unacked between attempts, so the retry count belongs to this message alone
   * and RabbitMQ redelivers it if the process dies mid-retry.
   */
  private void send(long deliveryTag, JsonObject json, int retries) {
    emailService
        .sendEmailService(json)
        .onComplete(
            result -> {
              if (result.succeeded()) {
                LOGGER.info(
                    "Email sent successfully for message with deliveryTag: {}", deliveryTag);
                rabbitMQClient.basicAck(deliveryTag, false);
                return;
              }
              LOGGER.error(
                  "Failed to send email for message with deliveryTag: {} (retry {} of {}). Error: {}",
                  deliveryTag,
                  retries,
                  MAX_RETRIES,
                  result.cause().getMessage());
              if (isPermanent(result.cause()) || retries >= MAX_RETRIES) {
                deadLetter(deliveryTag);
              } else {
                vertx.setTimer(RETRY_DELAY_MS, id -> send(deliveryTag, json, retries + 1));
              }
            });
  }

  private void deadLetter(long deliveryTag) {
    LOGGER.debug("sending message to dead letter queue");
    rabbitMQClient.basicNack(deliveryTag, false, false);
  }

  private static boolean isPermanent(Throwable cause) {
    return cause instanceof ServiceException serviceException
        && serviceException.failureCode() == EMAIL_PERMANENT_FAILURE_CODE;
  }
}
