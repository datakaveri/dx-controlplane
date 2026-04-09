package org.cdpg.dx.aaa.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.subscription.dao.SubscriptionServiceDAO;
import org.cdpg.dx.aaa.subscription.model.GetAllSubscription;
import org.cdpg.dx.aaa.subscription.model.GetSubscriptionModel;
import org.cdpg.dx.aaa.subscription.model.RegisterSubscription;
import org.cdpg.dx.aaa.subscription.model.SubscriptionDTO;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.databroker.model.RegisterQueueModel;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.databroker.util.PermissionOpType;
import org.cdpg.dx.databroker.util.Vhosts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("SubscriptionService Tests")
class SubscriptionServiceTest {

  @Mock private SubscriptionServiceDAO subscriptionServiceDAO;
  @Mock private DataBrokerService dataBrokerService;

  private SubscriptionServiceImpl subscriptionService;

  @BeforeEach
  void setUp() {
    subscriptionService = new SubscriptionServiceImpl(subscriptionServiceDAO, dataBrokerService);
  }

  @Nested
  @DisplayName("createSubscription")
  class CreateSubscription {

    @Test
    @DisplayName("should create a subscription and register queue in DataBroker")
    void createSubscription_success(VertxTestContext ctx) {
      String userId = UUID.randomUUID().toString();
      UUID subscriptionId = UUID.randomUUID();
      String subscriptionName = "test-sub";
      String entitiesId = UUID.randomUUID().toString();
      LocalDateTime expiryAt = LocalDateTime.now().plusDays(30);
      String providerId = UUID.randomUUID().toString();
      String did = "did:example:123";
      String queueName = userId + "/" + subscriptionName;

      RegisterQueueModel registerQueueModel =
          new RegisterQueueModel(userId, "apikey-123", queueName, "localhost", 5672, "/");

      when(dataBrokerService.registerQueue(eq(userId), eq(queueName), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture(registerQueueModel));
      when(dataBrokerService.queueBinding(
              eq(entitiesId), eq(queueName), eq(entitiesId), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              eq(userId), eq(queueName), eq(PermissionOpType.ADD_READ), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());
      when(subscriptionServiceDAO.insertSubscription(any(SubscriptionDTO.class)))
          .thenReturn(Future.succeededFuture());

      Future<RegisterSubscription> future =
          subscriptionService.createSubscription(
              userId, subscriptionId, subscriptionName, entitiesId, expiryAt, providerId, did);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.subscriptionId()).isEqualTo(subscriptionId.toString());
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.queueName()).isEqualTo(queueName);
            assertThat(result.apiKey()).isEqualTo("apikey-123");
          });
    }
  }

  @Nested
  @DisplayName("getAllSubscriptions")
  class GetSubscriptions {

    @Test
    @DisplayName("should return all subscriptions for a user")
    void getSubscriptions_success(VertxTestContext ctx) {
      String userId = UUID.randomUUID().toString();
      int limit = 10;
      int offset = 0;

      JsonArray resultArray =
          new JsonArray()
              .add(
                  new JsonObject()
                      .put("id", UUID.randomUUID().toString())
                      .put("queue_name", "test-queue"));

      GetAllSubscription expectedResult = new GetAllSubscription(resultArray, 1);

      when(subscriptionServiceDAO.getAllSubscriptionByUserId(userId, limit, offset))
          .thenReturn(Future.succeededFuture(expectedResult));

      Future<GetAllSubscription> future =
          subscriptionService.getAllSubscriptions(userId, limit, offset);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.count()).isEqualTo(1);
            assertThat(result.result()).hasSize(1);
          });
    }
  }

  @Nested
  @DisplayName("getSubscriptionById")
  class GetSubscriptionById {

    @Test
    @DisplayName("should return subscription when found")
    void getSubscriptionById_success(VertxTestContext ctx) {
      String subsId = UUID.randomUUID().toString();
      String userId = UUID.randomUUID().toString();
      String entityId = UUID.randomUUID().toString();
      String queueName = userId + "/test-sub";

      JsonArray metadataResult =
          new JsonArray()
              .add(
                  new JsonObject()
                      .put("entityId", entityId)
                      .put("queue_name", queueName));

      when(subscriptionServiceDAO.getEntitiesIdAndQueueNameBySubscriptionIdAndUserId(
              UUID.fromString(subsId), UUID.fromString(userId)))
          .thenReturn(Future.succeededFuture(metadataResult));
      when(dataBrokerService.listQueue(eq(queueName), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture(List.of("stream-1")));

      Future<GetSubscriptionModel> future =
          subscriptionService.getSubscriptionById(subsId, userId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.entities()).isEqualTo(entityId);
            assertThat(result.listString()).contains("stream-1");
          });
    }

    @Test
    @DisplayName("should fail when subscription not found")
    void getSubscriptionById_notFound(VertxTestContext ctx) {
      String subsId = UUID.randomUUID().toString();
      String userId = UUID.randomUUID().toString();

      when(subscriptionServiceDAO.getEntitiesIdAndQueueNameBySubscriptionIdAndUserId(
              UUID.fromString(subsId), UUID.fromString(userId)))
          .thenReturn(Future.failedFuture(new DxNotFoundException("Subscription not found")));

      Future<GetSubscriptionModel> future =
          subscriptionService.getSubscriptionById(subsId, userId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
          });
    }
  }

  @Nested
  @DisplayName("deleteSubscription")
  class DeleteSubscription {

    @Test
    @DisplayName("should delete subscription and queue from DataBroker")
    void deleteSubscription_success(VertxTestContext ctx) {
      String subsId = UUID.randomUUID().toString();
      String userId = UUID.randomUUID().toString();
      String entityId = UUID.randomUUID().toString();
      String queueName = userId + "/test-sub";

      JsonArray metadataResult =
          new JsonArray()
              .add(
                  new JsonObject()
                      .put("entityId", entityId)
                      .put("queue_name", queueName));

      when(subscriptionServiceDAO.getEntitiesIdAndQueueNameBySubscriptionIdAndUserId(
              UUID.fromString(subsId), UUID.fromString(userId)))
          .thenReturn(Future.succeededFuture(metadataResult));
      when(subscriptionServiceDAO.deleteSubscriptionBySubId(subsId))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.deleteQueue(eq(queueName), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              eq(userId), eq(queueName), eq(PermissionOpType.DELETE_READ), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());

      Future<Void> future = subscriptionService.deleteSubscription(subsId, userId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            verify(subscriptionServiceDAO).deleteSubscriptionBySubId(subsId);
            verify(dataBrokerService).deleteQueue(queueName, Vhosts.IUDX_PROD);
            verify(dataBrokerService)
                .updatePermission(userId, queueName, PermissionOpType.DELETE_READ, Vhosts.IUDX_PROD);
          });
    }
  }

  @Nested
  @DisplayName("updateSubscription")
  class UpdateSubscriptionExpiry {

    @Test
    @DisplayName("should update subscription expiry successfully")
    void updateSubscriptionExpiry_success(VertxTestContext ctx) {
      String entitiesId = UUID.randomUUID().toString();
      String subsId = UUID.randomUUID().toString();
      LocalDateTime newExpiry = LocalDateTime.now().plusDays(60);

      JsonArray existingResult =
          new JsonArray()
              .add(
                  new JsonObject()
                      .put("id", subsId)
                      .put("entityId", entitiesId));

      when(subscriptionServiceDAO.getSubscriptionBySubIdAndEntityId(subsId, entitiesId))
          .thenReturn(Future.succeededFuture(existingResult));
      when(subscriptionServiceDAO.updateSubscriptionExpiryByQueueNameAndEntityId(
              eq(subsId), eq(newExpiry)))
          .thenReturn(Future.succeededFuture());

      Future<Void> future = subscriptionService.updateSubscription(entitiesId, subsId, newExpiry);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            verify(subscriptionServiceDAO)
                .updateSubscriptionExpiryByQueueNameAndEntityId(subsId, newExpiry);
          });
    }
  }
}
