package org.cdpg.dx.databroker.listeners;

import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.leaderboard.enrichment.LeaderboardEnrichmentService;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;
import org.cdpg.dx.aaa.leaderboard.writer.LeaderboardWriterService;

import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.ACTION;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.LOG_TYPE;

public class LeaderboardConsumer implements RabitMqConsumer {

  private static final Logger LOGGER = LogManager.getLogger(LeaderboardConsumer.class);

  // Vote events are audited as USER_ACTION (shared with bookmarks, KYC, subscriptions, ...),
  // so they are picked by action rather than by logType.
  private static final Set<String> VOTE_ACTIONS = Set.of("LIKE", "DISLIKE", "NEUTRAL");

  // Broker-side prefetch bounds the unacked messages buffered in this consumer, and — since
  // processing is async — the number of concurrently running enrich+write pipelines. Must stay
  // below maxInternalQueueSize, otherwise the internal queue overflows and silently drops
  // messages under burst. 10 concurrent pipelines sustain hundreds of events/second, well above
  // real user-activity rates, while keeping the load on Elasticsearch/Postgres gentle.
  private static final int PREFETCH_COUNT = 10;

  private final RabbitMQClient rabbitMqClient;
  private final LeaderboardEnrichmentService enrichmentService;
  private final LeaderboardWriterService writerService;
  private final String queueName;
  private final QueueOptions queueOptions =
      new QueueOptions().setAutoAck(false).setMaxInternalQueueSize(100);
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
        .compose(v -> rabbitMqClient.basicQos(PREFETCH_COUNT))
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

          LOGGER.info(
              "LeaderboardConsumer started on queue {} (prefetch={})", queueName, PREFETCH_COUNT);
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

    if (!isLeaderboardRelevant(body)) {
      LOGGER.debug(
          "Ignoring non-leaderboard event [logType={}, action={}]",
          body.getString(LOG_TYPE),
          body.getString(ACTION));
      ack(deliveryTag);
      return;
    }

    LeaderboardEvent event;

    try {
      event = LeaderboardEvent.fromJson(body);
    } catch (Exception e) {
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
              // A message that already failed a redelivery is dropped so a poison event cannot
              // loop forever. One ERROR per failure, stating the outcome.
              if (message.envelope().isRedeliver()) {
                LOGGER.error(
                    "Leaderboard pipeline failed on redelivered message, dropping [assetId={},"
                        + " action={}]",
                    event.assetId(),
                    event.action(),
                    err);
                ack(deliveryTag);
              } else {
                LOGGER.error(
                    "Leaderboard pipeline failed, nacking [assetId={}, action={}]",
                    event.assetId(),
                    event.action(),
                    err);
                nack(deliveryTag);
              }
            });
  }

  /**
   * ASSET events (Create/Update/View/Download/Delete from the catalogue) always matter. Vote
   * events arrive as USER_ACTION, a logType shared with non-leaderboard audits (bookmarks, KYC,
   * subscriptions, ...), so those are filtered by action.
   */
  private boolean isLeaderboardRelevant(JsonObject body) {
    String logType = body.getString(LOG_TYPE);
    if ("asset".equalsIgnoreCase(logType)) {
      return true;
    }
    if ("USER_ACTION".equalsIgnoreCase(logType)) {
      String action = body.getString(ACTION);
      return action != null && VOTE_ACTIONS.contains(action.toUpperCase());
    }
    return false;
  }

  private void ack(long deliveryTag) {
    rabbitMqClient
        .basicAck(deliveryTag, false)
        .onFailure(err -> LOGGER.error("Failed to ACK message {}", deliveryTag, err));
  }

  private void nack(long deliveryTag) {
    // requeue = true → the broker redelivers the message for one retry
    rabbitMqClient
        .basicNack(deliveryTag, false, false)
        .onFailure(err -> LOGGER.error("Failed to NACK message {}", deliveryTag, err));
  }
}
