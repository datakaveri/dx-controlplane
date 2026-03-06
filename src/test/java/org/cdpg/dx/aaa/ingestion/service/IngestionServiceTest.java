package org.cdpg.dx.aaa.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.ExchangeNotFoundException;
import org.cdpg.dx.databroker.model.ExchangeSubscribersResponse;
import org.cdpg.dx.databroker.model.RegisterExchangeModel;
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
@DisplayName("IngestionService Tests")
class IngestionServiceTest {

  @Mock private DataBrokerService dataBrokerService;

  private IngestionServiceImpl ingestionService;

  @BeforeEach
  void setUp() {
    ingestionService = new IngestionServiceImpl(dataBrokerService);
  }

  @Nested
  @DisplayName("registerAdapter")
  class RegisterAdapter {

    @Test
    @DisplayName("should register adapter successfully with exchange, permission, and queue binding")
    void registerAdapter_success(VertxTestContext ctx) {
      String assetId = UUID.randomUUID().toString();
      String userId = UUID.randomUUID().toString();

      RegisterExchangeModel exchangeModel =
          new RegisterExchangeModel(userId, "api-key-123", assetId, "localhost", 5672, "/");

      when(dataBrokerService.registerExchange(eq(userId), eq(assetId), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture(exchangeModel));
      when(dataBrokerService.updatePermission(
              eq(userId), eq(assetId), eq(PermissionOpType.ADD_WRITE), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.queueBinding(
              eq(assetId), eq("database"), eq(assetId), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());

      Future<RegisterExchangeModel> future = ingestionService.registerAdapter(assetId, userId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getApiKey()).isEqualTo("api-key-123");
            assertThat(result.getExchangeName()).isEqualTo(assetId);
            assertThat(result.getUrl()).isEqualTo("localhost");
            assertThat(result.getPort()).isEqualTo(5672);

            verify(dataBrokerService).registerExchange(userId, assetId, Vhosts.IUDX_PROD);
            verify(dataBrokerService)
                .updatePermission(userId, assetId, PermissionOpType.ADD_WRITE, Vhosts.IUDX_PROD);
            verify(dataBrokerService)
                .queueBinding(assetId, "database", assetId, Vhosts.IUDX_PROD);
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException when assetId is null")
    void registerAdapter_nullAssetId(VertxTestContext ctx) {
      String userId = UUID.randomUUID().toString();

      Future<RegisterExchangeModel> future = ingestionService.registerAdapter(null, userId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxBadRequestException.class);
            assertThat(err.getMessage()).contains("Invalid input or blank value");
            verifyNoInteractions(dataBrokerService);
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException when assetId is empty")
    void registerAdapter_emptyAssetId(VertxTestContext ctx) {
      String userId = UUID.randomUUID().toString();

      Future<RegisterExchangeModel> future = ingestionService.registerAdapter("", userId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxBadRequestException.class);
            assertThat(err.getMessage()).contains("Invalid input or blank value");
            verifyNoInteractions(dataBrokerService);
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException when userId is null")
    void registerAdapter_nullUserId(VertxTestContext ctx) {
      String assetId = UUID.randomUUID().toString();

      Future<RegisterExchangeModel> future = ingestionService.registerAdapter(assetId, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxBadRequestException.class);
            assertThat(err.getMessage()).contains("Invalid input or blank value");
            verifyNoInteractions(dataBrokerService);
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException when userId is empty")
    void registerAdapter_emptyUserId(VertxTestContext ctx) {
      String assetId = UUID.randomUUID().toString();

      Future<RegisterExchangeModel> future = ingestionService.registerAdapter(assetId, "");

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxBadRequestException.class);
            assertThat(err.getMessage()).contains("Invalid input or blank value");
            verifyNoInteractions(dataBrokerService);
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException when both assetId and userId are null")
    void registerAdapter_bothNull(VertxTestContext ctx) {
      Future<RegisterExchangeModel> future = ingestionService.registerAdapter(null, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxBadRequestException.class);
            assertThat(err.getMessage()).contains("Invalid input or blank value");
            verifyNoInteractions(dataBrokerService);
          });
    }

    @Test
    @DisplayName("should fail when exchange creation fails in DataBroker")
    void registerAdapter_exchangeCreationFailure(VertxTestContext ctx) {
      String assetId = UUID.randomUUID().toString();
      String userId = UUID.randomUUID().toString();

      when(dataBrokerService.registerExchange(eq(userId), eq(assetId), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.failedFuture(new RuntimeException("Exchange creation failed")));

      Future<RegisterExchangeModel> future = ingestionService.registerAdapter(assetId, userId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Exchange creation failed");
          });
    }

    @Test
    @DisplayName("should fail when permission update fails after exchange creation")
    void registerAdapter_permissionUpdateFailure(VertxTestContext ctx) {
      String assetId = UUID.randomUUID().toString();
      String userId = UUID.randomUUID().toString();

      RegisterExchangeModel exchangeModel =
          new RegisterExchangeModel(userId, "api-key-123", assetId, "localhost", 5672, "/");

      when(dataBrokerService.registerExchange(eq(userId), eq(assetId), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture(exchangeModel));
      when(dataBrokerService.updatePermission(
              eq(userId), eq(assetId), eq(PermissionOpType.ADD_WRITE), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.failedFuture(new RuntimeException("Permission update failed")));

      Future<RegisterExchangeModel> future = ingestionService.registerAdapter(assetId, userId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Permission update failed");
          });
    }

    @Test
    @DisplayName("should fail when queue binding fails after permission update")
    void registerAdapter_queueBindingFailure(VertxTestContext ctx) {
      String assetId = UUID.randomUUID().toString();
      String userId = UUID.randomUUID().toString();

      RegisterExchangeModel exchangeModel =
          new RegisterExchangeModel(userId, "api-key-123", assetId, "localhost", 5672, "/");

      when(dataBrokerService.registerExchange(eq(userId), eq(assetId), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture(exchangeModel));
      when(dataBrokerService.updatePermission(
              eq(userId), eq(assetId), eq(PermissionOpType.ADD_WRITE), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.queueBinding(
              eq(assetId), eq("database"), eq(assetId), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.failedFuture(new RuntimeException("Queue binding failed")));

      Future<RegisterExchangeModel> future = ingestionService.registerAdapter(assetId, userId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Queue binding failed");
          });
    }
  }

  @Nested
  @DisplayName("deleteAdapter")
  class DeleteAdapter {

    @Test
    @DisplayName("should delete adapter successfully by removing exchange and permission")
    void deleteAdapter_success(VertxTestContext ctx) {
      String exchangeName = UUID.randomUUID().toString();
      String userId = UUID.randomUUID().toString();

      when(dataBrokerService.deleteExchange(eq(exchangeName), eq(userId), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              eq(userId),
              eq(exchangeName),
              eq(PermissionOpType.DELETE_WRITE),
              eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());

      Future<Void> future = ingestionService.deleteAdapter(exchangeName, userId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            verify(dataBrokerService).deleteExchange(exchangeName, userId, Vhosts.IUDX_PROD);
            verify(dataBrokerService)
                .updatePermission(
                    userId, exchangeName, PermissionOpType.DELETE_WRITE, Vhosts.IUDX_PROD);
          });
    }

    @Test
    @DisplayName("should fail when exchange deletion fails in DataBroker")
    void deleteAdapter_exchangeDeletionFailure(VertxTestContext ctx) {
      String exchangeName = UUID.randomUUID().toString();
      String userId = UUID.randomUUID().toString();

      when(dataBrokerService.deleteExchange(eq(exchangeName), eq(userId), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.failedFuture(new RuntimeException("Exchange deletion failed")));

      Future<Void> future = ingestionService.deleteAdapter(exchangeName, userId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Exchange deletion failed");
          });
    }

    @Test
    @DisplayName("should fail when permission removal fails after exchange deletion")
    void deleteAdapter_permissionRemovalFailure(VertxTestContext ctx) {
      String exchangeName = UUID.randomUUID().toString();
      String userId = UUID.randomUUID().toString();

      when(dataBrokerService.deleteExchange(eq(exchangeName), eq(userId), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              eq(userId),
              eq(exchangeName),
              eq(PermissionOpType.DELETE_WRITE),
              eq(Vhosts.IUDX_PROD)))
          .thenReturn(
              Future.failedFuture(new RuntimeException("Permission removal failed")));

      Future<Void> future = ingestionService.deleteAdapter(exchangeName, userId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Permission removal failed");
          });
    }
  }

  @Nested
  @DisplayName("getAdapterDetails")
  class GetAdapterDetails {

    @Test
    @DisplayName("should return adapter details when exchange has subscribers")
    void getAdapterDetails_success(VertxTestContext ctx) {
      String exchangeName = UUID.randomUUID().toString();

      Map<String, List<String>> subscriberMap =
          Map.of("queue-1", List.of("binding-key-1"), "queue-2", List.of("binding-key-2"));
      ExchangeSubscribersResponse subscribersResponse =
          new ExchangeSubscribersResponse(subscriberMap);

      when(dataBrokerService.listExchange(eq(exchangeName), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture(subscribersResponse));

      Future<ExchangeSubscribersResponse> future =
          ingestionService.getAdapterDetails(exchangeName);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getSubscribers()).hasSize(2);
            assertThat(result.getSubscribers()).containsKey("queue-1");
            assertThat(result.getSubscribers()).containsKey("queue-2");

            verify(dataBrokerService).listExchange(exchangeName, Vhosts.IUDX_PROD);
          });
    }

    @Test
    @DisplayName("should fail with ExchangeNotFoundException when exchange has no subscribers")
    void getAdapterDetails_noSubscribers(VertxTestContext ctx) {
      String exchangeName = UUID.randomUUID().toString();

      ExchangeSubscribersResponse emptyResponse =
          new ExchangeSubscribersResponse(Collections.emptyMap());

      when(dataBrokerService.listExchange(eq(exchangeName), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture(emptyResponse));

      Future<ExchangeSubscribersResponse> future =
          ingestionService.getAdapterDetails(exchangeName);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(ExchangeNotFoundException.class);
            assertThat(err.getMessage()).contains("Exchange not found");
          });
    }

    @Test
    @DisplayName("should return adapter details when exchange has a single subscriber")
    void getAdapterDetails_singleSubscriber(VertxTestContext ctx) {
      String exchangeName = UUID.randomUUID().toString();

      Map<String, List<String>> subscriberMap =
          Map.of("only-queue", List.of("routing-key-1"));
      ExchangeSubscribersResponse subscribersResponse =
          new ExchangeSubscribersResponse(subscriberMap);

      when(dataBrokerService.listExchange(eq(exchangeName), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture(subscribersResponse));

      Future<ExchangeSubscribersResponse> future =
          ingestionService.getAdapterDetails(exchangeName);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getSubscribers()).hasSize(1);
            assertThat(result.getSubscribers().get("only-queue"))
                .containsExactly("routing-key-1");
          });
    }

    @Test
    @DisplayName("should fail when DataBroker listExchange fails")
    void getAdapterDetails_dataBrokerFailure(VertxTestContext ctx) {
      String exchangeName = UUID.randomUUID().toString();

      when(dataBrokerService.listExchange(eq(exchangeName), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.failedFuture(new RuntimeException("DataBroker unavailable")));

      Future<ExchangeSubscribersResponse> future =
          ingestionService.getAdapterDetails(exchangeName);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("DataBroker unavailable");
          });
    }
  }
}
