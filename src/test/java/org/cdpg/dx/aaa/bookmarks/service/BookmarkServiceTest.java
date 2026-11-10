package org.cdpg.dx.aaa.bookmarks.service;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.bookmarks.dao.BookmarkDAO;
import org.cdpg.dx.aaa.bookmarks.model.Bookmark;
import org.cdpg.dx.aaa.bookmarks.model.BookmarkEntityType;
import org.cdpg.dx.aaa.bookmarks.service.impl.BookmarkServiceImpl;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.UpsertResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("BookmarkService Tests")
class BookmarkServiceTest {

  @Mock private BookmarkDAO bookmarkDAO;

  private BookmarkServiceImpl bookmarkService;

  @BeforeEach
  void setUp() {
    bookmarkService = new BookmarkServiceImpl(bookmarkDAO);
  }

  @Nested
  @DisplayName("createBookmark")
  class CreateBookmark {

    @Test
    @DisplayName("should create bookmark with valid databank entity type")
    void createBookmark_databank_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();
      String entityType = "databank";

      Bookmark createdBookmark =
          new Bookmark(UUID.randomUUID(), userId, entityId, BookmarkEntityType.DATABANK, "2025-01-01T00:00:00");
      UpsertResult<Bookmark> upsertResult = new UpsertResult<>(createdBookmark, true);

      when(bookmarkDAO.upsert(any(Bookmark.class)))
          .thenReturn(Future.succeededFuture(upsertResult));

      Future<UpsertResult<Bookmark>> future =
          bookmarkService.createBookmark(userId, entityId, entityType);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.created()).isTrue();
            assertThat(result.entity()).isNotNull();
            assertThat(result.entity().userId()).isEqualTo(userId);
            assertThat(result.entity().entityId()).isEqualTo(entityId);
            assertThat(result.entity().entityType()).isEqualTo(BookmarkEntityType.DATABANK);
            verify(bookmarkDAO).upsert(any(Bookmark.class));
          });
    }

    @Test
    @DisplayName("should create bookmark with valid ai_model entity type")
    void createBookmark_aiModel_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();
      String entityType = "ai_model";

      Bookmark createdBookmark =
          new Bookmark(UUID.randomUUID(), userId, entityId, BookmarkEntityType.AI_MODEL, "2025-01-01T00:00:00");
      UpsertResult<Bookmark> upsertResult = new UpsertResult<>(createdBookmark, true);

      when(bookmarkDAO.upsert(any(Bookmark.class)))
          .thenReturn(Future.succeededFuture(upsertResult));

      Future<UpsertResult<Bookmark>> future =
          bookmarkService.createBookmark(userId, entityId, entityType);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.entity().entityType()).isEqualTo(BookmarkEntityType.AI_MODEL);
            verify(bookmarkDAO).upsert(any(Bookmark.class));
          });
    }

    @Test
    @DisplayName("should create bookmark with valid usecase entity type")
    void createBookmark_usecase_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();
      String entityType = "usecase";

      Bookmark createdBookmark =
          new Bookmark(UUID.randomUUID(), userId, entityId, BookmarkEntityType.USECASE, "2025-01-01T00:00:00");
      UpsertResult<Bookmark> upsertResult = new UpsertResult<>(createdBookmark, true);

      when(bookmarkDAO.upsert(any(Bookmark.class)))
          .thenReturn(Future.succeededFuture(upsertResult));

      Future<UpsertResult<Bookmark>> future =
          bookmarkService.createBookmark(userId, entityId, entityType);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.entity().entityType()).isEqualTo(BookmarkEntityType.USECASE);
            verify(bookmarkDAO).upsert(any(Bookmark.class));
          });
    }

    @Test
    @DisplayName("should create bookmark with valid app entity type")
    void createBookmark_app_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();
      String entityType = "app";

      Bookmark createdBookmark =
          new Bookmark(UUID.randomUUID(), userId, entityId, BookmarkEntityType.APP, "2025-01-01T00:00:00");
      UpsertResult<Bookmark> upsertResult = new UpsertResult<>(createdBookmark, true);

      when(bookmarkDAO.upsert(any(Bookmark.class)))
          .thenReturn(Future.succeededFuture(upsertResult));

      Future<UpsertResult<Bookmark>> future =
          bookmarkService.createBookmark(userId, entityId, entityType);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.entity().entityType()).isEqualTo(BookmarkEntityType.APP);
            verify(bookmarkDAO).upsert(any(Bookmark.class));
          });
    }

    @Test
    @DisplayName("should pass bookmark with null id and null createdAt to DAO")
    void createBookmark_passesCorrectBookmarkToDAO(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();
      String entityType = "databank";

      ArgumentCaptor<Bookmark> captor = ArgumentCaptor.forClass(Bookmark.class);

      Bookmark createdBookmark =
          new Bookmark(UUID.randomUUID(), userId, entityId, BookmarkEntityType.DATABANK, "2025-01-01T00:00:00");
      UpsertResult<Bookmark> upsertResult = new UpsertResult<>(createdBookmark, true);

      when(bookmarkDAO.upsert(captor.capture()))
          .thenReturn(Future.succeededFuture(upsertResult));

      Future<UpsertResult<Bookmark>> future =
          bookmarkService.createBookmark(userId, entityId, entityType);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            Bookmark captured = captor.getValue();
            assertThat(captured.id()).isNull();
            assertThat(captured.userId()).isEqualTo(userId);
            assertThat(captured.entityId()).isEqualTo(entityId);
            assertThat(captured.entityType()).isEqualTo(BookmarkEntityType.DATABANK);
            assertThat(captured.createdAt()).isNull();
          });
    }

    @Test
    @DisplayName("should return upsert result with created=false when bookmark already exists")
    void createBookmark_alreadyExists_returnsCreatedFalse(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();
      String entityType = "databank";

      Bookmark existingBookmark =
          new Bookmark(UUID.randomUUID(), userId, entityId, BookmarkEntityType.DATABANK, "2025-01-01T00:00:00");
      UpsertResult<Bookmark> upsertResult = new UpsertResult<>(existingBookmark, false);

      when(bookmarkDAO.upsert(any(Bookmark.class)))
          .thenReturn(Future.succeededFuture(upsertResult));

      Future<UpsertResult<Bookmark>> future =
          bookmarkService.createBookmark(userId, entityId, entityType);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.created()).isFalse();
            assertThat(result.entity()).isNotNull();
          });
    }

    @Test
    @DisplayName("should fail when entity type is invalid")
    void createBookmark_invalidEntityType_fails(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();
      String invalidEntityType = "invalid_type";

      // BookmarkEntityType.fromValue will throw IllegalArgumentException synchronously,
      // which means the Future itself is never created. We catch the exception directly.
      try {
        bookmarkService.createBookmark(userId, entityId, invalidEntityType);
        ctx.failNow("Expected IllegalArgumentException to be thrown");
      } catch (IllegalArgumentException e) {
        ctx.verify(
            () -> {
              assertThat(e.getMessage()).contains("Invalid entityType");
              verifyNoInteractions(bookmarkDAO);
            });
        ctx.completeNow();
      }
    }

    @Test
    @DisplayName("should fail when DAO upsert fails")
    void createBookmark_daoFailure(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();
      String entityType = "databank";

      RuntimeException daoException = new RuntimeException("Database connection failed");
      when(bookmarkDAO.upsert(any(Bookmark.class)))
          .thenReturn(Future.failedFuture(daoException));

      Future<UpsertResult<Bookmark>> future =
          bookmarkService.createBookmark(userId, entityId, entityType);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Database connection failed");
            verify(bookmarkDAO).upsert(any(Bookmark.class));
          });
    }

    @Test
    @DisplayName("should handle case-insensitive entity type matching")
    void createBookmark_caseInsensitiveEntityType(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();
      String entityType = "DATABANK";

      Bookmark createdBookmark =
          new Bookmark(UUID.randomUUID(), userId, entityId, BookmarkEntityType.DATABANK, "2025-01-01T00:00:00");
      UpsertResult<Bookmark> upsertResult = new UpsertResult<>(createdBookmark, true);

      when(bookmarkDAO.upsert(any(Bookmark.class)))
          .thenReturn(Future.succeededFuture(upsertResult));

      Future<UpsertResult<Bookmark>> future =
          bookmarkService.createBookmark(userId, entityId, entityType);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.entity().entityType()).isEqualTo(BookmarkEntityType.DATABANK);
          });
    }
  }

  @Nested
  @DisplayName("getBookmarks")
  class GetBookmarks {

    @Test
    @DisplayName("should return paginated bookmarks for a user")
    void getBookmarks_success(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), null, null, null);

      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();
      Bookmark bookmark =
          new Bookmark(UUID.randomUUID(), userId, entityId, BookmarkEntityType.DATABANK, "2025-01-01T00:00:00");

      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 1);
      PaginatedResult<Bookmark> expectedResult =
          new PaginatedResult<>(paginationInfo, List.of(bookmark));

      when(bookmarkDAO.getByUser(any(PaginatedRequest.class)))
          .thenReturn(Future.succeededFuture(expectedResult));

      Future<PaginatedResult<Bookmark>> future = bookmarkService.getBookmarks(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).userId()).isEqualTo(userId);
            assertThat(result.data().get(0).entityId()).isEqualTo(entityId);
            assertThat(result.data().get(0).entityType()).isEqualTo(BookmarkEntityType.DATABANK);
            assertThat(result.paginationInfo()).isNotNull();
            assertThat(result.paginationInfo().getTotalCount()).isEqualTo(1);
            verify(bookmarkDAO).getByUser(request);
          });
    }

    @Test
    @DisplayName("should return empty list when user has no bookmarks")
    void getBookmarks_emptyResult(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), null, null, null);

      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 0);
      PaginatedResult<Bookmark> expectedResult =
          new PaginatedResult<Bookmark>(paginationInfo, List.of());

      when(bookmarkDAO.getByUser(any(PaginatedRequest.class)))
          .thenReturn(Future.succeededFuture(expectedResult));

      Future<PaginatedResult<Bookmark>> future = bookmarkService.getBookmarks(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.data()).isEmpty();
            assertThat(result.paginationInfo().getTotalCount()).isEqualTo(0);
            verify(bookmarkDAO).getByUser(request);
          });
    }

    @Test
    @DisplayName("should return multiple bookmarks with correct pagination")
    void getBookmarks_multipleResults(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), null, null, null);

      UUID userId = UUID.randomUUID();
      Bookmark bookmark1 =
          new Bookmark(UUID.randomUUID(), userId, UUID.randomUUID(), BookmarkEntityType.DATABANK, "2025-01-01T00:00:00");
      Bookmark bookmark2 =
          new Bookmark(UUID.randomUUID(), userId, UUID.randomUUID(), BookmarkEntityType.AI_MODEL, "2025-01-02T00:00:00");
      Bookmark bookmark3 =
          new Bookmark(UUID.randomUUID(), userId, UUID.randomUUID(), BookmarkEntityType.USECASE, "2025-01-03T00:00:00");

      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 3);
      PaginatedResult<Bookmark> expectedResult =
          new PaginatedResult<>(paginationInfo, List.of(bookmark1, bookmark2, bookmark3));

      when(bookmarkDAO.getByUser(any(PaginatedRequest.class)))
          .thenReturn(Future.succeededFuture(expectedResult));

      Future<PaginatedResult<Bookmark>> future = bookmarkService.getBookmarks(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.data()).hasSize(3);
            assertThat(result.paginationInfo().getTotalCount()).isEqualTo(3);
            verify(bookmarkDAO).getByUser(request);
          });
    }

    @Test
    @DisplayName("should delegate request directly to DAO without modification")
    void getBookmarks_delegatesToDAO(VertxTestContext ctx) {
      PaginatedRequest request =
          new PaginatedRequest(2, 5, Map.of("key", "value"), null, null, null);

      PaginationInfo paginationInfo = PaginationInfo.from(2, 5, 0);
      PaginatedResult<Bookmark> expectedResult =
          new PaginatedResult<Bookmark>(paginationInfo, List.of());

      when(bookmarkDAO.getByUser(eq(request)))
          .thenReturn(Future.succeededFuture(expectedResult));

      Future<PaginatedResult<Bookmark>> future = bookmarkService.getBookmarks(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            verify(bookmarkDAO).getByUser(request);
          });
    }

    @Test
    @DisplayName("should fail when DAO getByUser fails")
    void getBookmarks_daoFailure(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), null, null, null);

      RuntimeException daoException = new RuntimeException("Query execution failed");
      when(bookmarkDAO.getByUser(any(PaginatedRequest.class)))
          .thenReturn(Future.failedFuture(daoException));

      Future<PaginatedResult<Bookmark>> future = bookmarkService.getBookmarks(request);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Query execution failed");
            verify(bookmarkDAO).getByUser(request);
          });
    }
  }

  @Nested
  @DisplayName("deleteBookmark")
  class DeleteBookmark {

    @Test
    @DisplayName("should delete bookmark successfully")
    void deleteBookmark_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();

      when(bookmarkDAO.delete(eq(userId), eq(entityId)))
          .thenReturn(Future.succeededFuture());

      Future<Void> future = bookmarkService.deleteBookmark(userId, entityId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNull();
            verify(bookmarkDAO).delete(userId, entityId);
          });
    }

    @Test
    @DisplayName("should pass correct userId and entityId to DAO")
    void deleteBookmark_passesCorrectArguments(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();

      ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
      ArgumentCaptor<UUID> entityIdCaptor = ArgumentCaptor.forClass(UUID.class);

      when(bookmarkDAO.delete(userIdCaptor.capture(), entityIdCaptor.capture()))
          .thenReturn(Future.succeededFuture());

      Future<Void> future = bookmarkService.deleteBookmark(userId, entityId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(userIdCaptor.getValue()).isEqualTo(userId);
            assertThat(entityIdCaptor.getValue()).isEqualTo(entityId);
          });
    }

    @Test
    @DisplayName("should fail when DAO delete fails")
    void deleteBookmark_daoFailure(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();

      RuntimeException daoException = new RuntimeException("Delete operation failed");
      when(bookmarkDAO.delete(eq(userId), eq(entityId)))
          .thenReturn(Future.failedFuture(daoException));

      Future<Void> future = bookmarkService.deleteBookmark(userId, entityId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Delete operation failed");
            verify(bookmarkDAO).delete(userId, entityId);
          });
    }

    @Test
    @DisplayName("should fail when bookmark not found for deletion")
    void deleteBookmark_notFound(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID entityId = UUID.randomUUID();

      when(bookmarkDAO.delete(eq(userId), eq(entityId)))
          .thenReturn(Future.failedFuture(new RuntimeException("Bookmark not found")));

      Future<Void> future = bookmarkService.deleteBookmark(userId, entityId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).isEqualTo("Bookmark not found");
            verify(bookmarkDAO).delete(userId, entityId);
          });
    }
  }
}
