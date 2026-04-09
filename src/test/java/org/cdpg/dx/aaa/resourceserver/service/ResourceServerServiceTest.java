package org.cdpg.dx.aaa.resourceserver.service;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.cdpg.dx.aaa.resourceserver.dao.ResourceServerDAO;
import org.cdpg.dx.aaa.resourceserver.models.ResourceServer;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.exception.NoRowFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ResourceServerServiceTest {

  @Mock
  private ResourceServerDAO dao;

  private ResourceServerServiceImpl service;

  @BeforeEach
  void setUp(VertxTestContext vertxTestContext) {
    service = new ResourceServerServiceImpl(dao);
    vertxTestContext.completeNow();
  }

  // ---------------------------------------------------------------------------
  // create()
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("create()")
  class Create {

    @Test
    @DisplayName("Success - delegates to DAO and returns created resource server")
    void testCreate_success(VertxTestContext testContext) {
      ResourceServer rs = mock(ResourceServer.class);
      ResourceServer created = mock(ResourceServer.class);

      when(dao.create(rs)).thenReturn(Future.succeededFuture(created));

      service.create(rs).onComplete(ar -> {
        if (ar.succeeded()) {
          assertEquals(created, ar.result());
          verify(dao).create(rs);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - propagates DAO failure")
    void testCreate_failure(VertxTestContext testContext) {
      ResourceServer rs = mock(ResourceServer.class);
      RuntimeException error = new RuntimeException("DB error");

      when(dao.create(rs)).thenReturn(Future.failedFuture(error));

      service.create(rs).onComplete(ar -> {
        if (ar.failed()) {
          assertEquals("DB error", ar.cause().getMessage());
          verify(dao).create(rs);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }
  }

  // ---------------------------------------------------------------------------
  // get()
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("get()")
  class Get {

    @Test
    @DisplayName("Success - returns resource server by id")
    void testGet_success(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      ResourceServer rs = mock(ResourceServer.class);

      when(dao.get(id)).thenReturn(Future.succeededFuture(rs));

      service.get(id).onComplete(ar -> {
        if (ar.succeeded()) {
          assertEquals(rs, ar.result());
          verify(dao).get(id);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - wraps NoRowFoundException as DxNotFoundException")
    void testGet_noRowFound(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      NoRowFoundException noRow = new NoRowFoundException("no row");

      when(dao.get(id)).thenReturn(Future.failedFuture(noRow));

      service.get(id).onComplete(ar -> {
        if (ar.failed()) {
          assertTrue(ar.cause() instanceof DxNotFoundException);
          assertTrue(ar.cause().getMessage().contains("No matching requestId found"));
          verify(dao).get(id);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - propagates non-NoRowFound exception through BaseDxException.from()")
    void testGet_otherException(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      BaseDxException otherError = new BaseDxException("some other error");

      when(dao.get(id)).thenReturn(Future.failedFuture(otherError));

      service.get(id).onComplete(ar -> {
        if (ar.failed()) {
          assertFalse(ar.cause() instanceof DxNotFoundException);
          assertTrue(ar.cause() instanceof BaseDxException);
          verify(dao).get(id);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }
  }

  // ---------------------------------------------------------------------------
  // getAllByRole()
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getAllByRole()")
  class GetAllByRole {

    @Test
    @DisplayName("Success - cos_admin gets all resource servers via dao.getAll()")
    void testGetAllByRole_cosAdmin_success(VertxTestContext testContext) {
      UUID userId = UUID.randomUUID();
      List<ResourceServer> rsList = List.of(mock(ResourceServer.class), mock(ResourceServer.class));

      when(dao.getAll()).thenReturn(Future.succeededFuture(rsList));

      service.getAllByRole("cos_admin", userId).onComplete(ar -> {
        if (ar.succeeded()) {
          assertEquals(rsList, ar.result());
          assertEquals(2, ar.result().size());
          verify(dao).getAll();
          verify(dao, never()).getAllWithFilters(anyMap());
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Success - org_admin gets filtered resource servers by owner_id")
    void testGetAllByRole_orgAdmin_success(VertxTestContext testContext) {
      UUID userId = UUID.randomUUID();
      List<ResourceServer> rsList = List.of(mock(ResourceServer.class));
      Map<String, Object> expectedFilters = Map.of("owner_id", userId.toString());

      when(dao.getAllWithFilters(expectedFilters)).thenReturn(Future.succeededFuture(rsList));

      service.getAllByRole("org_admin", userId).onComplete(ar -> {
        if (ar.succeeded()) {
          assertEquals(rsList, ar.result());
          assertEquals(1, ar.result().size());
          verify(dao).getAllWithFilters(expectedFilters);
          verify(dao, never()).getAll();
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - cos_admin NoRowFoundException wrapped as DxNotFoundException")
    void testGetAllByRole_cosAdmin_noRowFound(VertxTestContext testContext) {
      UUID userId = UUID.randomUUID();
      NoRowFoundException noRow = new NoRowFoundException("no rows");

      when(dao.getAll()).thenReturn(Future.failedFuture(noRow));

      service.getAllByRole("cos_admin", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertTrue(ar.cause() instanceof DxNotFoundException);
          verify(dao).getAll();
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - cos_admin other exception propagated")
    void testGetAllByRole_cosAdmin_otherError(VertxTestContext testContext) {
      UUID userId = UUID.randomUUID();
      BaseDxException otherError = new BaseDxException("db error");

      when(dao.getAll()).thenReturn(Future.failedFuture(otherError));

      service.getAllByRole("cos_admin", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertFalse(ar.cause() instanceof DxNotFoundException);
          assertTrue(ar.cause() instanceof BaseDxException);
          verify(dao).getAll();
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - org_admin NoRowFoundException wrapped as DxNotFoundException")
    void testGetAllByRole_orgAdmin_noRowFound(VertxTestContext testContext) {
      UUID userId = UUID.randomUUID();
      Map<String, Object> expectedFilters = Map.of("owner_id", userId.toString());
      NoRowFoundException noRow = new NoRowFoundException("no rows");

      when(dao.getAllWithFilters(expectedFilters)).thenReturn(Future.failedFuture(noRow));

      service.getAllByRole("org_admin", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertTrue(ar.cause() instanceof DxNotFoundException);
          verify(dao).getAllWithFilters(expectedFilters);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - org_admin other exception propagated")
    void testGetAllByRole_orgAdmin_otherError(VertxTestContext testContext) {
      UUID userId = UUID.randomUUID();
      Map<String, Object> expectedFilters = Map.of("owner_id", userId.toString());
      BaseDxException otherError = new BaseDxException("db error");

      when(dao.getAllWithFilters(expectedFilters)).thenReturn(Future.failedFuture(otherError));

      service.getAllByRole("org_admin", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertFalse(ar.cause() instanceof DxNotFoundException);
          assertTrue(ar.cause() instanceof BaseDxException);
          verify(dao).getAllWithFilters(expectedFilters);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }
  }

  // ---------------------------------------------------------------------------
  // deleteByRole()
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("deleteByRole()")
  class DeleteByRole {

    // --- cos_admin branch ---

    @Test
    @DisplayName("Success - cos_admin deletes any resource server")
    void testDeleteByRole_cosAdmin_success(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      when(dao.delete(id)).thenReturn(Future.succeededFuture(true));

      service.deleteByRole(id, "cos_admin", userId).onComplete(ar -> {
        if (ar.succeeded()) {
          assertTrue(ar.result());
          verify(dao).delete(id);
          verify(dao, never()).get(any());
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - cos_admin delete NoRowFoundException wrapped as DxNotFoundException")
    void testDeleteByRole_cosAdmin_noRowFound(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      NoRowFoundException noRow = new NoRowFoundException("no row");

      when(dao.delete(id)).thenReturn(Future.failedFuture(noRow));

      service.deleteByRole(id, "cos_admin", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertTrue(ar.cause() instanceof DxNotFoundException);
          verify(dao).delete(id);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - cos_admin delete other exception propagated")
    void testDeleteByRole_cosAdmin_otherError(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      BaseDxException otherError = new BaseDxException("db error");

      when(dao.delete(id)).thenReturn(Future.failedFuture(otherError));

      service.deleteByRole(id, "cos_admin", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertFalse(ar.cause() instanceof DxNotFoundException);
          assertTrue(ar.cause() instanceof BaseDxException);
          verify(dao).delete(id);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    // --- org_admin branch ---

    @Test
    @DisplayName("Success - org_admin deletes own resource server")
    void testDeleteByRole_orgAdmin_ownerMatch_success(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      ResourceServer rs = mock(ResourceServer.class);

      when(rs.ownerId()).thenReturn(userId);
      when(dao.get(id)).thenReturn(Future.succeededFuture(rs));
      when(dao.delete(id)).thenReturn(Future.succeededFuture(true));

      service.deleteByRole(id, "org_admin", userId).onComplete(ar -> {
        if (ar.succeeded()) {
          assertTrue(ar.result());
          verify(dao).get(id);
          verify(dao).delete(id);
          testContext.completeNow();
        } else {
          testContext.failNow(ar.cause());
        }
      });
    }

    @Test
    @DisplayName("Failure - org_admin cannot delete resource server owned by someone else")
    void testDeleteByRole_orgAdmin_ownerMismatch(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID differentOwnerId = UUID.randomUUID();
      ResourceServer rs = mock(ResourceServer.class);

      when(rs.ownerId()).thenReturn(differentOwnerId);
      when(dao.get(id)).thenReturn(Future.succeededFuture(rs));

      service.deleteByRole(id, "org_admin", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertTrue(ar.cause() instanceof DxUnauthorizedException);
          assertTrue(ar.cause().getMessage().contains("You can only delete resource servers you own"));
          verify(dao).get(id);
          verify(dao, never()).delete(any());
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - org_admin delete when ownerId is null")
    void testDeleteByRole_orgAdmin_ownerIdNull(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      ResourceServer rs = mock(ResourceServer.class);

      when(rs.ownerId()).thenReturn(null);
      when(dao.get(id)).thenReturn(Future.succeededFuture(rs));

      service.deleteByRole(id, "org_admin", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertTrue(ar.cause() instanceof DxUnauthorizedException);
          verify(dao).get(id);
          verify(dao, never()).delete(any());
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - org_admin get fails with NoRowFoundException wrapped as DxNotFoundException")
    void testDeleteByRole_orgAdmin_noRowFound(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      NoRowFoundException noRow = new NoRowFoundException("no row");

      when(dao.get(id)).thenReturn(Future.failedFuture(noRow));

      service.deleteByRole(id, "org_admin", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertTrue(ar.cause() instanceof DxNotFoundException);
          verify(dao).get(id);
          verify(dao, never()).delete(any());
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - org_admin get fails with other exception propagated")
    void testDeleteByRole_orgAdmin_otherError(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      BaseDxException otherError = new BaseDxException("db error");

      when(dao.get(id)).thenReturn(Future.failedFuture(otherError));

      service.deleteByRole(id, "org_admin", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertFalse(ar.cause() instanceof DxNotFoundException);
          assertTrue(ar.cause() instanceof BaseDxException);
          verify(dao).get(id);
          verify(dao, never()).delete(any());
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    // --- unsupported role branch ---

    @Test
    @DisplayName("Failure - unsupported role returns DxUnauthorizedException")
    void testDeleteByRole_unsupportedRole(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      service.deleteByRole(id, "consumer", userId).onComplete(ar -> {
        if (ar.failed()) {
          assertTrue(ar.cause() instanceof DxUnauthorizedException);
          assertTrue(ar.cause().getMessage().contains("Not Authorized"));
          verifyNoInteractions(dao);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }

    @Test
    @DisplayName("Failure - null role returns DxUnauthorizedException")
    void testDeleteByRole_nullRole(VertxTestContext testContext) {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      service.deleteByRole(id, null, userId).onComplete(ar -> {
        if (ar.failed()) {
          assertTrue(ar.cause() instanceof DxUnauthorizedException);
          assertTrue(ar.cause().getMessage().contains("Not Authorized"));
          verifyNoInteractions(dao);
          testContext.completeNow();
        } else {
          testContext.failNow("Expected failure but got success");
        }
      });
    }
  }
}
