package org.cdpg.dx.databroker.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import org.cdpg.dx.databroker.util.PermissionOpType;
import org.cdpg.dx.databroker.util.Vhosts;
import org.cdpg.dx.testutil.RabbitMQTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for {@link DataBrokerServiceImpl} against a real RabbitMQ container.
 *
 * <p>Tests are ordered to manage resource lifecycle (create before delete).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataBrokerServiceIT extends RabbitMQTestBase {

  private static final String TEST_EXCHANGE = "test-exchange-" + UUID.randomUUID().toString().substring(0, 8);
  private static final String TEST_QUEUE = "test-queue-" + UUID.randomUUID().toString().substring(0, 8);
  private static final String TEST_USER_ID = "test-user-" + UUID.randomUUID().toString().substring(0, 8);

  @Test
  @Order(1)
  void registerExchange_createsExchange(VertxTestContext ctx) {
    dataBrokerService
        .registerExchange(TEST_USER_ID, TEST_EXCHANGE, Vhosts.IUDX_PROD)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isNotNull();
                          assertThat(result.getExchangeName())
                              .isEqualTo(TEST_EXCHANGE);
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(2)
  void registerExchange_duplicate_fails(VertxTestContext ctx) {
    // The exchange was already created in order 1
    dataBrokerService
        .registerExchange(TEST_USER_ID, TEST_EXCHANGE, Vhosts.IUDX_PROD)
        .onComplete(
            ctx.failing(
                err ->
                    ctx.verify(
                        () -> {
                          assertThat(err.getMessage()).isNotNull();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(3)
  void registerQueue_createsQueueAndUser(VertxTestContext ctx) {
    dataBrokerService
        .registerQueue(TEST_USER_ID, TEST_QUEUE, Vhosts.IUDX_PROD)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isNotNull();
                          assertThat(result.getQueueName())
                              .isEqualTo(TEST_QUEUE);
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(4)
  void registerQueue_existingUser_returnsApiKeyMessage(VertxTestContext ctx) {
    String anotherQueue = "another-queue-" + UUID.randomUUID().toString().substring(0, 8);
    dataBrokerService
        .registerQueue(TEST_USER_ID, anotherQueue, Vhosts.IUDX_PROD)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isNotNull();
                          // User already exists, so apiKey should contain the message
                          assertThat(result.toJson().getString("apiKey")).isNotNull();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(5)
  void queueBinding_bindsQueueToExchange(VertxTestContext ctx) {
    dataBrokerService
        .queueBinding(TEST_EXCHANGE, TEST_QUEUE, "test.routing.key", Vhosts.IUDX_PROD)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          // Void result means success
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(6)
  void listExchange_returnsSubscribers(VertxTestContext ctx) {
    dataBrokerService
        .listExchange(TEST_EXCHANGE, Vhosts.IUDX_PROD)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isNotNull();
                          // Should have at least the binding we created
                          assertThat(result.getSubscribers()).isNotNull();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(7)
  void listQueue_returnsRoutingKeys(VertxTestContext ctx) {
    dataBrokerService
        .listQueue(TEST_QUEUE, Vhosts.IUDX_PROD)
        .onComplete(
            ctx.succeeding(
                routingKeys ->
                    ctx.verify(
                        () -> {
                          assertThat(routingKeys).isNotNull();
                          assertThat(routingKeys).contains("test.routing.key");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(8)
  void updatePermission_addsReadPermission(VertxTestContext ctx) {
    dataBrokerService
        .updatePermission(
            TEST_USER_ID, TEST_QUEUE, PermissionOpType.ADD_READ, Vhosts.IUDX_PROD)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          // Void result means success
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(9)
  void resetPassword_changesPassword(VertxTestContext ctx) {
    dataBrokerService
        .resetPassword(TEST_USER_ID)
        .onComplete(
            ctx.succeeding(
                newPassword ->
                    ctx.verify(
                        () -> {
                          assertThat(newPassword).isNotNull();
                          assertThat(newPassword).isNotEmpty();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(10)
  void publishMessageInternal_publishes(VertxTestContext ctx) {
    // First create an exchange in the internal vhost
    String internalExchange = "internal-exchange-" + UUID.randomUUID().toString().substring(0, 8);
    String internalUserId = "internal-user-" + UUID.randomUUID().toString().substring(0, 8);

    dataBrokerService
        .registerExchange(internalUserId, internalExchange, Vhosts.IUDX_INTERNAL)
        .compose(
            registered -> {
              JsonObject message = new JsonObject().put("test", "message").put("key", "value");
              return dataBrokerService.publishMessageInternal(
                  message, internalExchange, "internal.routing.key");
            })
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          // Void result means publish succeeded
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(11)
  void publishMessageExternal_publishes(VertxTestContext ctx) {
    // Use the exchange created in the prod vhost
    JsonArray messages = new JsonArray().add(new JsonObject().put("data", "test-external"));

    dataBrokerService
        .publishMessageExternal(TEST_EXCHANGE, "external.routing.key", messages)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isEqualTo("success");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(50)
  void deleteQueue_removesQueue(VertxTestContext ctx) {
    // Create a queue to delete
    String deleteQueue = "delete-queue-" + UUID.randomUUID().toString().substring(0, 8);
    String deleteUserId = "delete-user-" + UUID.randomUUID().toString().substring(0, 8);
    dataBrokerService
        .registerQueue(deleteUserId, deleteQueue, Vhosts.IUDX_PROD)
        .compose(registered -> dataBrokerService.deleteQueue(deleteQueue, Vhosts.IUDX_PROD))
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          // Void result means success
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(51)
  void deleteQueue_nonExistent_fails(VertxTestContext ctx) {
    dataBrokerService
        .deleteQueue("non-existent-queue-xyz", Vhosts.IUDX_PROD)
        .onComplete(
            ctx.failing(
                err ->
                    ctx.verify(
                        () -> {
                          assertThat(err.getMessage()).isNotNull();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(52)
  void deleteExchange_removesExchange(VertxTestContext ctx) {
    // Create an exchange to delete
    String deleteExchange = "delete-exchange-" + UUID.randomUUID().toString().substring(0, 8);
    String deleteUserId = "delete-user2-" + UUID.randomUUID().toString().substring(0, 8);

    dataBrokerService
        .registerExchange(deleteUserId, deleteExchange, Vhosts.IUDX_PROD)
        .compose(
            registered ->
                dataBrokerService.deleteExchange(deleteExchange, deleteUserId, Vhosts.IUDX_PROD))
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          // Void result means success
                          ctx.completeNow();
                        })));
  }
}
