package org.cdpg.dx.databroker.listeners;

import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.email.service.EmailService;

public class EmailMessageConsumer implements RabitMqConsumer {
  private static final Logger LOGGER = LogManager.getLogger(EmailMessageConsumer.class);
  static int COUNTER = 0;
  private final RabbitMQClient rabbitMQClient;
  private final String queueName;
  private final QueueOptions options =
      new QueueOptions().setMaxInternalQueueSize(100).setKeepMostRecent(true).setAutoAck(false);
  private final EmailService emailService;

  public EmailMessageConsumer(
      RabbitMQClient rabbitMQClient, String queueName, EmailService emailService) {
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
    JsonObject json = message.body().toJsonObject();
    /*EmailRequest emailRequest = EmailRequest.fromJson(json);*/
    emailService
        .sendEmailService(json)
        .onComplete(
            success -> {
              if (success.succeeded()) {
                LOGGER.info(
                    "Email sent successfully for message with deliveryTag: {}", deliveryTag);
                rabbitMQClient.basicAck(deliveryTag, false);
              } else {
                LOGGER.error(
                    "Failed to send email for message with deliveryTag: {}. Error: {}",
                    deliveryTag,
                    success.cause().getMessage());
                if (COUNTER < 6) {
                  rabbitMQClient.basicNack(deliveryTag, false, true);
                  try {
                    Thread.sleep(5000);
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                  COUNTER++;
                } else {
                  LOGGER.debug("sending message to dead letter queue");
                  rabbitMQClient.basicNack(deliveryTag, false, false);
                  COUNTER = 0;
                }
              }
            });
  }
}
