package org.cdpg.dx.aaa.asset.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.cdpg.dx.aaa.asset.dao.AssetRequestDAO;
import org.cdpg.dx.aaa.asset.models.AssetRequest;
import org.cdpg.dx.aaa.asset.models.Status;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.NoRowFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.aaa.asset.util.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class AssetServiceTest {

  @Mock
  private AssetRequestDAO assetRequestDAO;

  private AssetServiceImpl assetService;
  private JsonObject config;

  @BeforeEach
  void setUp(VertxTestContext vertxTestContext) {
    config = new JsonObject();
    assetService = new AssetServiceImpl(assetRequestDAO, config);
    vertxTestContext.completeNow();
  }

  // ---------------------------------------------------------------------------
  // Helper to build a sample AssetRequest
  // ---------------------------------------------------------------------------
  private AssetRequest sampleAssetRequest(UUID id, UUID assetId, UUID userId) {
    return new AssetRequest(
      id,
      assetId,
      userId,
      Status.PENDING.getStatus(),
      "dataset",
      new JsonObject().put("key", "value"),
      LocalDateTime.now(),
      LocalDateTime.now()
    );
  }

  // ===========================================================================
  @Nested
  @DisplayName("createAssetRequest")
  class CreateAssetRequest {

    @Test
    @DisplayName("Success - should create an asset request and return it")
    void testCreateAssetRequest_success(VertxTestContext testContext) {
      // Arrange
      UUID id = UUID.randomUUID();
      UUID assetId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AssetRequest request = sampleAssetRequest(id, assetId, userId);

      when(assetRequestDAO.create(request)).thenReturn(Future.succeededFuture(request));

      // Act
      assetService.createAssetRequest(request).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert
          assertEquals(request, ar.result());
          verify(assetRequestDAO).create(request);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - should propagate error when DAO fails")
    void testCreateAssetRequest_failure(VertxTestContext testContext) {
      // Arrange
      UUID id = UUID.randomUUID();
      UUID assetId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AssetRequest request = sampleAssetRequest(id, assetId, userId);

      RuntimeException daoError = new RuntimeException("Database connection failed");
      when(assetRequestDAO.create(request)).thenReturn(Future.failedFuture(daoError));

      // Act
      assetService.createAssetRequest(request).onComplete(ar -> {
        if (ar.failed()) {
          // Assert
          assertEquals("Database connection failed", ar.cause().getMessage());
          verify(assetRequestDAO).create(request);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }
  }

  // ===========================================================================
  @Nested
  @DisplayName("getAllAssetRequest")
  class GetAllAssetRequest {

    @Test
    @DisplayName("Success - should return paginated asset requests")
    void testGetAllAssetRequest_success(VertxTestContext testContext) {
      // Arrange
      PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);
      AssetRequest req1 = mock(AssetRequest.class);
      AssetRequest req2 = mock(AssetRequest.class);
      List<AssetRequest> requestList = List.of(req1, req2);
      PaginationInfo paginationInfo = mock(PaginationInfo.class);
      PaginatedResult<AssetRequest> expectedResult = new PaginatedResult<>(paginationInfo, requestList);

      when(assetRequestDAO.getAllWithFilters(paginatedRequest))
        .thenReturn(Future.succeededFuture(expectedResult));

      // Act
      assetService.getAllAssetRequest(paginatedRequest).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert
          assertEquals(expectedResult, ar.result());
          assertEquals(2, ar.result().data().size());
          verify(assetRequestDAO).getAllWithFilters(paginatedRequest);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Success - should return empty paginated result when no requests exist")
    void testGetAllAssetRequest_emptyResult(VertxTestContext testContext) {
      // Arrange
      PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);
      PaginationInfo paginationInfo = mock(PaginationInfo.class);
      PaginatedResult<AssetRequest> emptyResult = new PaginatedResult<AssetRequest>(paginationInfo, Collections.emptyList());

      when(assetRequestDAO.getAllWithFilters(paginatedRequest))
        .thenReturn(Future.succeededFuture(emptyResult));

      // Act
      assetService.getAllAssetRequest(paginatedRequest).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert
          assertTrue(ar.result().data().isEmpty());
          verify(assetRequestDAO).getAllWithFilters(paginatedRequest);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - should propagate error when DAO fails")
    void testGetAllAssetRequest_failure(VertxTestContext testContext) {
      // Arrange
      PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);

      when(assetRequestDAO.getAllWithFilters(paginatedRequest))
        .thenReturn(Future.failedFuture(new RuntimeException("Query failed")));

      // Act
      assetService.getAllAssetRequest(paginatedRequest).onComplete(ar -> {
        if (ar.failed()) {
          // Assert
          assertEquals("Query failed", ar.cause().getMessage());
          verify(assetRequestDAO).getAllWithFilters(paginatedRequest);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }
  }

  // ===========================================================================
  @Nested
  @DisplayName("updateAssetRequestStatus")
  class UpdateAssetRequestStatus {

    @Test
    @DisplayName("Success - should return true when status is updated to GRANTED")
    void testUpdateStatus_granted(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      Status status = Status.GRANTED;
      AssetRequest updatedRequest = mock(AssetRequest.class);

      when(assetRequestDAO.update(anyMap(), anyMap()))
        .thenReturn(Future.succeededFuture(updatedRequest));

      // Act
      assetService.updateAssetRequestStatus(requestId, status).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert
          assertTrue(ar.result());
          verify(assetRequestDAO).update(anyMap(), anyMap());
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Success - should return true when status is updated to REJECTED")
    void testUpdateStatus_rejected(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      Status status = Status.REJECTED;
      AssetRequest updatedRequest = mock(AssetRequest.class);

      when(assetRequestDAO.update(anyMap(), anyMap()))
        .thenReturn(Future.succeededFuture(updatedRequest));

      // Act
      assetService.updateAssetRequestStatus(requestId, status).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert
          assertTrue(ar.result());
          verify(assetRequestDAO).update(anyMap(), anyMap());
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - should fail with DxBadRequestException when status is PENDING")
    void testUpdateStatus_pendingFails(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      Status status = Status.PENDING;
      AssetRequest updatedRequest = mock(AssetRequest.class);

      when(assetRequestDAO.update(anyMap(), anyMap()))
        .thenReturn(Future.succeededFuture(updatedRequest));

      // Act
      assetService.updateAssetRequestStatus(requestId, status).onComplete(ar -> {
        if (ar.failed()) {
          // Assert
          assertInstanceOf(DxBadRequestException.class, ar.cause());
          assertTrue(ar.cause().getMessage().contains("Invalid status for asset request"));
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - should fail with DxNotFoundException when asset request not found")
    void testUpdateStatus_notFound(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      Status status = Status.GRANTED;

      when(assetRequestDAO.update(anyMap(), anyMap()))
        .thenReturn(Future.failedFuture(new NoRowFoundException("No row found")));

      // Act
      assetService.updateAssetRequestStatus(requestId, status).onComplete(ar -> {
        if (ar.failed()) {
          // Assert
          assertInstanceOf(DxNotFoundException.class, ar.cause());
          assertTrue(ar.cause().getMessage().contains("Asset request not found with ID:"));
          assertTrue(ar.cause().getMessage().contains(requestId.toString()));
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - should propagate unknown errors from DAO")
    void testUpdateStatus_unknownError(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      Status status = Status.GRANTED;

      RuntimeException unknownError = new RuntimeException("Unexpected DB error");
      when(assetRequestDAO.update(anyMap(), anyMap()))
        .thenReturn(Future.failedFuture(unknownError));

      // Act
      assetService.updateAssetRequestStatus(requestId, status).onComplete(ar -> {
        if (ar.failed()) {
          // Assert
          assertInstanceOf(RuntimeException.class, ar.cause());
          assertEquals("Unexpected DB error", ar.cause().getMessage());
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Success - should pass correct condition and update maps to DAO")
    void testUpdateStatus_correctMaps(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      Status status = Status.GRANTED;
      AssetRequest updatedRequest = mock(AssetRequest.class);

      when(assetRequestDAO.update(anyMap(), anyMap()))
        .thenReturn(Future.succeededFuture(updatedRequest));

      // Act
      assetService.updateAssetRequestStatus(requestId, status).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert - verify the DAO was called with maps containing expected keys
          verify(assetRequestDAO).update(
            argThat(conditionMap ->
              conditionMap.containsKey(ASSET_REQUEST_ID) &&
                conditionMap.get(ASSET_REQUEST_ID).equals(requestId.toString())
            ),
            argThat(updateMap ->
              updateMap.containsKey(STATUS) &&
                updateMap.get(STATUS).equals(status.getStatus()) &&
                updateMap.containsKey(UPDATED_AT)
            )
          );
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }
  }

  // ===========================================================================
  @Nested
  @DisplayName("getAssetRequestById")
  class GetAssetRequestById {

    @Test
    @DisplayName("Success - should return true when asset request exists for user")
    void testGetById_exists(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AssetRequest assetRequest = mock(AssetRequest.class);
      List<AssetRequest> resultList = List.of(assetRequest);

      Map<String, Object> expectedFilter = Map.of(
        ASSET_ID, requestId.toString(),
        USER_ID, userId.toString()
      );

      when(assetRequestDAO.getAllWithFilters(expectedFilter))
        .thenReturn(Future.succeededFuture(resultList));

      // Act
      assetService.getAssetRequestById(requestId, userId).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert
          assertTrue(ar.result());
          verify(assetRequestDAO).getAllWithFilters(expectedFilter);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Success - should return false when no asset request exists for user")
    void testGetById_notExists(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Map<String, Object> expectedFilter = Map.of(
        ASSET_ID, requestId.toString(),
        USER_ID, userId.toString()
      );

      when(assetRequestDAO.getAllWithFilters(expectedFilter))
        .thenReturn(Future.succeededFuture(Collections.emptyList()));

      // Act
      assetService.getAssetRequestById(requestId, userId).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert
          assertFalse(ar.result());
          verify(assetRequestDAO).getAllWithFilters(expectedFilter);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Success - should return false when result list is null")
    void testGetById_nullResult(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Map<String, Object> expectedFilter = Map.of(
        ASSET_ID, requestId.toString(),
        USER_ID, userId.toString()
      );

      when(assetRequestDAO.getAllWithFilters(expectedFilter))
        .thenReturn(Future.succeededFuture(null));

      // Act
      assetService.getAssetRequestById(requestId, userId).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert
          assertFalse(ar.result());
          verify(assetRequestDAO).getAllWithFilters(expectedFilter);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - should propagate error when DAO fails")
    void testGetById_daoFailure(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      Map<String, Object> expectedFilter = Map.of(
        ASSET_ID, requestId.toString(),
        USER_ID, userId.toString()
      );

      when(assetRequestDAO.getAllWithFilters(expectedFilter))
        .thenReturn(Future.failedFuture(new RuntimeException("DB error")));

      // Act
      assetService.getAssetRequestById(requestId, userId).onComplete(ar -> {
        if (ar.failed()) {
          // Assert
          assertEquals("DB error", ar.cause().getMessage());
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }
  }

  // ===========================================================================
  @Nested
  @DisplayName("getAssetRequestDetailsById")
  class GetAssetRequestDetailsById {

    @Test
    @DisplayName("Success - should return asset request when found")
    void testGetDetailsById_success(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();
      UUID assetId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AssetRequest expectedRequest = sampleAssetRequest(requestId, assetId, userId);

      when(assetRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(expectedRequest));

      // Act
      assetService.getAssetRequestDetailsById(requestId).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert
          assertEquals(expectedRequest, ar.result());
          verify(assetRequestDAO).get(requestId);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - should fail with DxNotFoundException when request is null")
    void testGetDetailsById_notFound(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();

      when(assetRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(null));

      // Act
      assetService.getAssetRequestDetailsById(requestId).onComplete(ar -> {
        if (ar.failed()) {
          // Assert
          assertInstanceOf(DxNotFoundException.class, ar.cause());
          assertTrue(ar.cause().getMessage().contains("Asset Request not found!"));
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - should propagate error when DAO fails")
    void testGetDetailsById_daoFailure(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();

      when(assetRequestDAO.get(requestId))
        .thenReturn(Future.failedFuture(new RuntimeException("Connection lost")));

      // Act
      assetService.getAssetRequestDetailsById(requestId).onComplete(ar -> {
        if (ar.failed()) {
          // Assert
          assertEquals("Connection lost", ar.cause().getMessage());
          verify(assetRequestDAO).get(requestId);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }
  }

  // ===========================================================================
  @Nested
  @DisplayName("deleteAssetRequestById")
  class DeleteAssetRequestById {

    @Test
    @DisplayName("Success - should return true when asset request is deleted")
    void testDelete_success(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();

      when(assetRequestDAO.delete(requestId)).thenReturn(Future.succeededFuture(true));

      // Act
      assetService.deleteAssetRequestById(requestId).onComplete(ar -> {
        if (ar.succeeded()) {
          // Assert
          assertTrue(ar.result());
          verify(assetRequestDAO).delete(requestId);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - should throw DxNotFoundException when no row is deleted")
    void testDelete_notFound(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();

      when(assetRequestDAO.delete(requestId)).thenReturn(Future.succeededFuture(false));

      // Act
      assetService.deleteAssetRequestById(requestId).onComplete(ar -> {
        if (ar.failed()) {
          // Assert
          assertInstanceOf(DxNotFoundException.class, ar.cause());
          assertTrue(ar.cause().getMessage().contains("Asset request not found with ID:"));
          assertTrue(ar.cause().getMessage().contains(requestId.toString()));
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - should propagate error when DAO fails")
    void testDelete_daoFailure(VertxTestContext testContext) {
      // Arrange
      UUID requestId = UUID.randomUUID();

      when(assetRequestDAO.delete(requestId))
        .thenReturn(Future.failedFuture(new RuntimeException("Delete failed")));

      // Act
      assetService.deleteAssetRequestById(requestId).onComplete(ar -> {
        if (ar.failed()) {
          // Assert
          assertEquals("Delete failed", ar.cause().getMessage());
          verify(assetRequestDAO).delete(requestId);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }
  }
}
