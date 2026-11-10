package org.cdpg.dx.aaa.connector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.cdpg.dx.common.exception.DxBadRequestException;
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
@DisplayName("ConnectorService Tests")
class ConnectorServiceTest {

  private static final String PUBLISH_EX = "test-exchange";
  private static final String USER_ID = "test-user-id";
  private static final String ASSET_ID = "test-asset-id";

  @Mock private DataBrokerService dataBrokerService;

  private ConnectorServiceImpl connectorService;

  @BeforeEach
  void setUp() {
    connectorService = new ConnectorServiceImpl(dataBrokerService, PUBLISH_EX);
  }

  private RegisterQueueModel buildQueueModel() {
    return new RegisterQueueModel(
        USER_ID, "test-api-key", ASSET_ID, "localhost", 5672, "internalVhost");
  }

  @Nested
  @DisplayName("createConnector")
  class CreateConnector {

    @Test
    @DisplayName("should create connector successfully when all steps succeed")
    void createConnector_success(VertxTestContext ctx) {
      RegisterQueueModel queueModel = buildQueueModel();

      when(dataBrokerService.registerQueue(USER_ID, ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture(queueModel));
      when(dataBrokerService.queueBinding(PUBLISH_EX, ASSET_ID, ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              USER_ID, ASSET_ID, PermissionOpType.ADD_READ, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              USER_ID, "amq.default", PermissionOpType.ADD_WRITE, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture());

      Future<RegisterQueueModel> future = connectorService.createConnector(USER_ID, ASSET_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getQueueName()).isEqualTo(ASSET_ID);
            assertThat(result.getApiKey()).isEqualTo("test-api-key");
            assertThat(result.getUrl()).isEqualTo("localhost");
            assertThat(result.getPort()).isEqualTo(5672);

            verify(dataBrokerService).registerQueue(USER_ID, ASSET_ID, Vhosts.IUDX_INTERNAL);
            verify(dataBrokerService)
                .queueBinding(PUBLISH_EX, ASSET_ID, ASSET_ID, Vhosts.IUDX_INTERNAL);
            verify(dataBrokerService)
                .updatePermission(
                    USER_ID, ASSET_ID, PermissionOpType.ADD_READ, Vhosts.IUDX_INTERNAL);
            verify(dataBrokerService)
                .updatePermission(
                    USER_ID, "amq.default", PermissionOpType.ADD_WRITE, Vhosts.IUDX_INTERNAL);
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException when userId is null")
    void createConnector_nullUserId(VertxTestContext ctx) {
      Future<RegisterQueueModel> future = connectorService.createConnector(null, ASSET_ID);

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
    void createConnector_emptyUserId(VertxTestContext ctx) {
      Future<RegisterQueueModel> future = connectorService.createConnector("", ASSET_ID);

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
    @DisplayName("should fail with DxBadRequestException when assetId is null")
    void createConnector_nullAssetId(VertxTestContext ctx) {
      Future<RegisterQueueModel> future = connectorService.createConnector(USER_ID, null);

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
    void createConnector_emptyAssetId(VertxTestContext ctx) {
      Future<RegisterQueueModel> future = connectorService.createConnector(USER_ID, "");

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
    @DisplayName("should fail with DxBadRequestException when both userId and assetId are null")
    void createConnector_bothNull(VertxTestContext ctx) {
      Future<RegisterQueueModel> future = connectorService.createConnector(null, null);

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
    @DisplayName("should fail when queue registration fails")
    void createConnector_registerQueueFailure(VertxTestContext ctx) {
      RuntimeException registerError = new RuntimeException("Queue registration failed");

      when(dataBrokerService.registerQueue(USER_ID, ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.failedFuture(registerError));

      Future<RegisterQueueModel> future = connectorService.createConnector(USER_ID, ASSET_ID);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Queue registration failed");
            verify(dataBrokerService).registerQueue(USER_ID, ASSET_ID, Vhosts.IUDX_INTERNAL);
          });
    }

    @Test
    @DisplayName("should fail when queue binding fails after successful queue creation")
    void createConnector_queueBindingFailure(VertxTestContext ctx) {
      RegisterQueueModel queueModel = buildQueueModel();
      RuntimeException bindingError = new RuntimeException("Queue binding failed");

      when(dataBrokerService.registerQueue(USER_ID, ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture(queueModel));
      when(dataBrokerService.queueBinding(PUBLISH_EX, ASSET_ID, ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.failedFuture(bindingError));

      Future<RegisterQueueModel> future = connectorService.createConnector(USER_ID, ASSET_ID);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Queue binding failed");
            verify(dataBrokerService).registerQueue(USER_ID, ASSET_ID, Vhosts.IUDX_INTERNAL);
            verify(dataBrokerService)
                .queueBinding(PUBLISH_EX, ASSET_ID, ASSET_ID, Vhosts.IUDX_INTERNAL);
          });
    }

    @Test
    @DisplayName("should fail when ADD_READ permission update fails")
    void createConnector_addReadPermissionFailure(VertxTestContext ctx) {
      RegisterQueueModel queueModel = buildQueueModel();
      RuntimeException permError = new RuntimeException("Read permission update failed");

      when(dataBrokerService.registerQueue(USER_ID, ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture(queueModel));
      when(dataBrokerService.queueBinding(PUBLISH_EX, ASSET_ID, ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              USER_ID, ASSET_ID, PermissionOpType.ADD_READ, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.failedFuture(permError));

      Future<RegisterQueueModel> future = connectorService.createConnector(USER_ID, ASSET_ID);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Read permission update failed");
          });
    }

    @Test
    @DisplayName("should fail when ADD_WRITE permission update fails")
    void createConnector_addWritePermissionFailure(VertxTestContext ctx) {
      RegisterQueueModel queueModel = buildQueueModel();
      RuntimeException permError = new RuntimeException("Write permission update failed");

      when(dataBrokerService.registerQueue(USER_ID, ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture(queueModel));
      when(dataBrokerService.queueBinding(PUBLISH_EX, ASSET_ID, ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              USER_ID, ASSET_ID, PermissionOpType.ADD_READ, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              USER_ID, "amq.default", PermissionOpType.ADD_WRITE, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.failedFuture(permError));

      Future<RegisterQueueModel> future = connectorService.createConnector(USER_ID, ASSET_ID);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Write permission update failed");
          });
    }
  }

  @Nested
  @DisplayName("deleteConnector")
  class DeleteConnector {

    @Test
    @DisplayName("should delete connector successfully when all steps succeed")
    void deleteConnector_success(VertxTestContext ctx) {
      when(dataBrokerService.deleteQueue(ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              USER_ID, ASSET_ID, PermissionOpType.DELETE_READ, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture());

      Future<Void> future = connectorService.deleteConnector(USER_ID, ASSET_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNull();
            verify(dataBrokerService).deleteQueue(ASSET_ID, Vhosts.IUDX_INTERNAL);
            verify(dataBrokerService)
                .updatePermission(
                    USER_ID, ASSET_ID, PermissionOpType.DELETE_READ, Vhosts.IUDX_INTERNAL);
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException when userId is null")
    void deleteConnector_nullUserId(VertxTestContext ctx) {
      Future<Void> future = connectorService.deleteConnector(null, ASSET_ID);

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
    void deleteConnector_emptyUserId(VertxTestContext ctx) {
      Future<Void> future = connectorService.deleteConnector("", ASSET_ID);

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
    @DisplayName("should fail with DxBadRequestException when assetId is null")
    void deleteConnector_nullAssetId(VertxTestContext ctx) {
      Future<Void> future = connectorService.deleteConnector(USER_ID, null);

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
    void deleteConnector_emptyAssetId(VertxTestContext ctx) {
      Future<Void> future = connectorService.deleteConnector(USER_ID, "");

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
    @DisplayName("should fail with DxBadRequestException when both userId and assetId are null")
    void deleteConnector_bothNull(VertxTestContext ctx) {
      Future<Void> future = connectorService.deleteConnector(null, null);

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
    @DisplayName("should fail when queue deletion fails")
    void deleteConnector_deleteQueueFailure(VertxTestContext ctx) {
      RuntimeException deleteError = new RuntimeException("Queue deletion failed");

      when(dataBrokerService.deleteQueue(ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.failedFuture(deleteError));

      Future<Void> future = connectorService.deleteConnector(USER_ID, ASSET_ID);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Queue deletion failed");
            verify(dataBrokerService).deleteQueue(ASSET_ID, Vhosts.IUDX_INTERNAL);
          });
    }

    @Test
    @DisplayName("should fail when DELETE_READ permission update fails after queue deletion")
    void deleteConnector_permissionUpdateFailure(VertxTestContext ctx) {
      RuntimeException permError = new RuntimeException("Permission update failed");

      when(dataBrokerService.deleteQueue(ASSET_ID, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              USER_ID, ASSET_ID, PermissionOpType.DELETE_READ, Vhosts.IUDX_INTERNAL))
          .thenReturn(Future.failedFuture(permError));

      Future<Void> future = connectorService.deleteConnector(USER_ID, ASSET_ID);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Permission update failed");
            verify(dataBrokerService).deleteQueue(ASSET_ID, Vhosts.IUDX_INTERNAL);
            verify(dataBrokerService)
                .updatePermission(
                    USER_ID, ASSET_ID, PermissionOpType.DELETE_READ, Vhosts.IUDX_INTERNAL);
          });
    }
  }
}
