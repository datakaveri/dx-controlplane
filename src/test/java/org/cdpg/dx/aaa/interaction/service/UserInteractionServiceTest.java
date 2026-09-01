package org.cdpg.dx.aaa.interaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.interaction.dao.UserInteractionDao;
import org.cdpg.dx.aaa.interaction.enums.ActionType;
import org.cdpg.dx.aaa.interaction.enums.InteractionValue;
import org.cdpg.dx.aaa.interaction.model.*;
import org.cdpg.dx.aaa.interaction.service.impl.UserInteractionServiceImpl;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.elastic.model.BulkSyncResult;
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
@DisplayName("UserInteractionService Tests")
class UserInteractionServiceTest {

  @Mock private UserInteractionDao dao;
  @Mock private ItemService itemService;

  private UserInteractionServiceImpl service;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID ENTITY_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new UserInteractionServiceImpl(dao, itemService);
  }

  // ---------------------------------------------------------------------------
  // handleInteraction
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("handleInteraction")
  class HandleInteraction {

    @Nested
    @DisplayName("new interaction (no existing)")
    class NewInteraction {

      @Test
      @DisplayName("should upsert a new LIKE and update engagement counters")
      void newLike_upsertsAndUpdatesCounters(VertxTestContext ctx) {
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.LIKE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.succeededFuture(null));

        UserInteraction expectedInteraction =
            new UserInteraction(null, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.LIKE);
        when(dao.upsert(any(UserInteraction.class)))
            .thenReturn(Future.succeededFuture(new UpsertResult<>(expectedInteraction, true)));

        when(itemService.updateEngagementCounters(ENTITY_ID, 1, 0))
            .thenReturn(Future.succeededFuture());

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureSuccess(
            future,
            ctx,
            result -> {
              ArgumentCaptor<UserInteraction> captor = ArgumentCaptor.forClass(UserInteraction.class);
              verify(dao).upsert(captor.capture());

              UserInteraction captured = captor.getValue();
              assertThat(captured.id()).isNull();
              assertThat(captured.userId()).isEqualTo(USER_ID);
              assertThat(captured.entityId()).isEqualTo(ENTITY_ID);
              assertThat(captured.entityType()).isEqualTo(EntityType.ASSET);
              assertThat(captured.actionType()).isEqualTo(ActionType.VOTE);
              assertThat(captured.value()).isEqualTo(InteractionValue.LIKE);

              verify(itemService).updateEngagementCounters(ENTITY_ID, 1, 0);
              verify(dao, never()).delete(any(), any(), any());
            });
      }

      @Test
      @DisplayName("should upsert a new DISLIKE and update engagement counters")
      void newDislike_upsertsAndUpdatesCounters(VertxTestContext ctx) {
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.DISLIKE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.succeededFuture(null));
        when(dao.upsert(any(UserInteraction.class)))
            .thenReturn(Future.succeededFuture(
                new UpsertResult<>(
                    new UserInteraction(null, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.DISLIKE),
                    true)));
        when(itemService.updateEngagementCounters(ENTITY_ID, 0, 1))
            .thenReturn(Future.succeededFuture());

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureSuccess(
            future,
            ctx,
            result -> {
              verify(dao).upsert(any(UserInteraction.class));
              verify(itemService).updateEngagementCounters(ENTITY_ID, 0, 1);
            });
      }

      @Test
      @DisplayName("should upsert a BOOKMARK without updating engagement counters")
      void newBookmark_upsertsWithoutCounterUpdate(VertxTestContext ctx) {
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.BOOKMARK, InteractionValue.ADD);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.BOOKMARK))
            .thenReturn(Future.succeededFuture(null));
        when(dao.upsert(any(UserInteraction.class)))
            .thenReturn(Future.succeededFuture(
                new UpsertResult<>(
                    new UserInteraction(null, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.BOOKMARK, InteractionValue.ADD),
                    true)));

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureSuccess(
            future,
            ctx,
            result -> {
              verify(dao).upsert(any(UserInteraction.class));
              // BOOKMARK action produces a no-op delta, so engagement counters should not be touched
              verifyNoInteractions(itemService);
            });
      }
    }

    @Nested
    @DisplayName("changing an existing interaction")
    class ChangeInteraction {

      @Test
      @DisplayName("should change LIKE to DISLIKE and update engagement counters with -1/+1")
      void likeToDislike(VertxTestContext ctx) {
        UUID existingId = UUID.randomUUID();
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.DISLIKE);

        UserInteraction existing =
            new UserInteraction(existingId, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.LIKE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.succeededFuture(existing));
        when(dao.upsert(any(UserInteraction.class)))
            .thenReturn(Future.succeededFuture(
                new UpsertResult<>(
                    new UserInteraction(existingId, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.DISLIKE),
                    false)));
        when(itemService.updateEngagementCounters(ENTITY_ID, -1, 1))
            .thenReturn(Future.succeededFuture());

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureSuccess(
            future,
            ctx,
            result -> {
              ArgumentCaptor<UserInteraction> captor = ArgumentCaptor.forClass(UserInteraction.class);
              verify(dao).upsert(captor.capture());

              UserInteraction captured = captor.getValue();
              assertThat(captured.id()).isEqualTo(existingId);
              assertThat(captured.value()).isEqualTo(InteractionValue.DISLIKE);

              verify(itemService).updateEngagementCounters(ENTITY_ID, -1, 1);
            });
      }

      @Test
      @DisplayName("should change DISLIKE to LIKE and update engagement counters with +1/-1")
      void dislikeToLike(VertxTestContext ctx) {
        UUID existingId = UUID.randomUUID();
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.LIKE);

        UserInteraction existing =
            new UserInteraction(existingId, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.DISLIKE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.succeededFuture(existing));
        when(dao.upsert(any(UserInteraction.class)))
            .thenReturn(Future.succeededFuture(
                new UpsertResult<>(
                    new UserInteraction(existingId, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.LIKE),
                    false)));
        when(itemService.updateEngagementCounters(ENTITY_ID, 1, -1))
            .thenReturn(Future.succeededFuture());

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureSuccess(
            future,
            ctx,
            result -> {
              verify(dao).upsert(any(UserInteraction.class));
              verify(itemService).updateEngagementCounters(ENTITY_ID, 1, -1);
            });
      }

      @Test
      @DisplayName("should be a no-op delta when voting same value as existing")
      void sameVoteAsExisting_noOpDelta(VertxTestContext ctx) {
        UUID existingId = UUID.randomUUID();
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.LIKE);

        UserInteraction existing =
            new UserInteraction(existingId, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.LIKE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.succeededFuture(existing));
        when(dao.upsert(any(UserInteraction.class)))
            .thenReturn(Future.succeededFuture(new UpsertResult<>(existing, false)));

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureSuccess(
            future,
            ctx,
            result -> {
              verify(dao).upsert(any(UserInteraction.class));
              // Same value as existing means delta is (0,0) -- no-op
              verifyNoInteractions(itemService);
            });
      }
    }

    @Nested
    @DisplayName("REMOVE interaction")
    class RemoveInteraction {

      @Test
      @DisplayName("should delete existing LIKE and decrement like counter")
      void removeLike_deletesAndDecrementsCounter(VertxTestContext ctx) {
        UUID existingId = UUID.randomUUID();
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.REMOVE);

        UserInteraction existing =
            new UserInteraction(existingId, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.LIKE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.succeededFuture(existing));
        when(dao.delete(USER_ID, ENTITY_ID, ActionType.VOTE.name()))
            .thenReturn(Future.succeededFuture());
        when(itemService.updateEngagementCounters(ENTITY_ID, -1, 0))
            .thenReturn(Future.succeededFuture());

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureSuccess(
            future,
            ctx,
            result -> {
              verify(dao).delete(USER_ID, ENTITY_ID, ActionType.VOTE.name());
              verify(dao, never()).upsert(any());
              verify(itemService).updateEngagementCounters(ENTITY_ID, -1, 0);
            });
      }

      @Test
      @DisplayName("should delete existing DISLIKE and decrement dislike counter")
      void removeDislike_deletesAndDecrementsCounter(VertxTestContext ctx) {
        UUID existingId = UUID.randomUUID();
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.REMOVE);

        UserInteraction existing =
            new UserInteraction(existingId, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.DISLIKE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.succeededFuture(existing));
        when(dao.delete(USER_ID, ENTITY_ID, ActionType.VOTE.name()))
            .thenReturn(Future.succeededFuture());
        when(itemService.updateEngagementCounters(ENTITY_ID, 0, -1))
            .thenReturn(Future.succeededFuture());

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureSuccess(
            future,
            ctx,
            result -> {
              verify(dao).delete(USER_ID, ENTITY_ID, ActionType.VOTE.name());
              verify(dao, never()).upsert(any());
              verify(itemService).updateEngagementCounters(ENTITY_ID, 0, -1);
            });
      }

      @Test
      @DisplayName("should delete with no-op delta when no existing vote")
      void removeWithoutExisting_deletesWithNoOpDelta(VertxTestContext ctx) {
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.REMOVE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.succeededFuture(null));
        when(dao.delete(USER_ID, ENTITY_ID, ActionType.VOTE.name()))
            .thenReturn(Future.succeededFuture());

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureSuccess(
            future,
            ctx,
            result -> {
              verify(dao).delete(USER_ID, ENTITY_ID, ActionType.VOTE.name());
              verify(dao, never()).upsert(any());
              // oldValue=null, newValue=null -> delta (0,0) -> no-op
              verifyNoInteractions(itemService);
            });
      }

      @Test
      @DisplayName("should delete BOOKMARK REMOVE without updating engagement counters")
      void removeBookmark_deletesWithoutCounterUpdate(VertxTestContext ctx) {
        UUID existingId = UUID.randomUUID();
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.BOOKMARK, InteractionValue.REMOVE);

        UserInteraction existing =
            new UserInteraction(existingId, USER_ID, ENTITY_ID, EntityType.ASSET, ActionType.BOOKMARK, InteractionValue.ADD);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.BOOKMARK))
            .thenReturn(Future.succeededFuture(existing));
        when(dao.delete(USER_ID, ENTITY_ID, ActionType.BOOKMARK.name()))
            .thenReturn(Future.succeededFuture());

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureSuccess(
            future,
            ctx,
            result -> {
              verify(dao).delete(USER_ID, ENTITY_ID, ActionType.BOOKMARK.name());
              // BOOKMARK never triggers engagement counter updates
              verifyNoInteractions(itemService);
            });
      }
    }

    @Nested
    @DisplayName("DAO failure scenarios")
    class DaoFailure {

      @Test
      @DisplayName("should propagate failure when fetchExistingInteraction fails")
      void fetchExistingFails_propagatesError(VertxTestContext ctx) {
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.LIKE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.failedFuture(new RuntimeException("DB connection failed")));

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureFailure(
            future,
            ctx,
            err -> {
              assertThat(err).isInstanceOf(RuntimeException.class);
              assertThat(err.getMessage()).isEqualTo("DB connection failed");
              verify(dao, never()).upsert(any());
              verify(dao, never()).delete(any(), any(), any());
              verifyNoInteractions(itemService);
            });
      }

      @Test
      @DisplayName("should propagate failure when upsert fails")
      void upsertFails_propagatesError(VertxTestContext ctx) {
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.LIKE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.succeededFuture(null));
        when(dao.upsert(any(UserInteraction.class)))
            .thenReturn(Future.failedFuture(new RuntimeException("Upsert failed")));

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureFailure(
            future,
            ctx,
            err -> {
              assertThat(err).isInstanceOf(RuntimeException.class);
              assertThat(err.getMessage()).isEqualTo("Upsert failed");
              verifyNoInteractions(itemService);
            });
      }

      @Test
      @DisplayName("should propagate failure when delete fails")
      void deleteFails_propagatesError(VertxTestContext ctx) {
        InteractionRequest req =
            new InteractionRequest(ENTITY_ID, EntityType.ASSET, ActionType.VOTE, InteractionValue.REMOVE);

        when(dao.fetchExistingInteraction(USER_ID, ENTITY_ID, ActionType.VOTE))
            .thenReturn(Future.succeededFuture(null));
        when(dao.delete(USER_ID, ENTITY_ID, ActionType.VOTE.name()))
            .thenReturn(Future.failedFuture(new RuntimeException("Delete failed")));

        Future<Void> future = service.handleInteraction(USER_ID, req);

        assertFutureFailure(
            future,
            ctx,
            err -> {
              assertThat(err).isInstanceOf(RuntimeException.class);
              assertThat(err.getMessage()).isEqualTo("Delete failed");
              verifyNoInteractions(itemService);
            });
      }
    }
  }

  // ---------------------------------------------------------------------------
  // getUserInteractions
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getUserInteractions")
  class GetUserInteractions {

    @Test
    @DisplayName("should return paginated interactions on success")
    void success_returnsPaginatedResponse(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), null, null, null);

      InteractionRow row =
          new InteractionRow(ENTITY_ID.toString(), EntityType.ASSET, true, true, false);
      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 1);
      UserInteractionsPaginatedResponse expectedResponse =
          new UserInteractionsPaginatedResponse(List.of(row), paginationInfo);

      when(dao.fetchUserInteractions(request))
          .thenReturn(Future.succeededFuture(expectedResponse));

      Future<UserInteractionsPaginatedResponse> future = service.getUserInteractions(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.data()).hasSize(1);

            InteractionRow returnedRow = result.data().get(0);
            assertThat(returnedRow.entityId()).isEqualTo(ENTITY_ID.toString());
            assertThat(returnedRow.entityType()).isEqualTo(EntityType.ASSET);
            assertThat(returnedRow.isBookmarked()).isTrue();
            assertThat(returnedRow.isLiked()).isTrue();
            assertThat(returnedRow.isDisliked()).isFalse();

            assertThat(result.paginationInfo()).isNotNull();
            assertThat(result.paginationInfo().getTotalCount()).isEqualTo(1);

            verify(dao).fetchUserInteractions(request);
          });
    }

    @Test
    @DisplayName("should return empty list when no interactions exist")
    void empty_returnsEmptyList(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), null, null, null);

      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 0);
      UserInteractionsPaginatedResponse emptyResponse =
          new UserInteractionsPaginatedResponse(List.of(), paginationInfo);

      when(dao.fetchUserInteractions(request))
          .thenReturn(Future.succeededFuture(emptyResponse));

      Future<UserInteractionsPaginatedResponse> future = service.getUserInteractions(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.data()).isEmpty();
            assertThat(result.paginationInfo().getTotalCount()).isEqualTo(0);

            verify(dao).fetchUserInteractions(request);
          });
    }

    @Test
    @DisplayName("should propagate failure when DAO fails")
    void daoFailure_propagatesError(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), null, null, null);

      when(dao.fetchUserInteractions(request))
          .thenReturn(Future.failedFuture(new RuntimeException("Query failed")));

      Future<UserInteractionsPaginatedResponse> future = service.getUserInteractions(request);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Query failed");

            verify(dao).fetchUserInteractions(request);
          });
    }
  }

  // ---------------------------------------------------------------------------
  // syncInteractionMetrics
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("syncInteractionMetrics")
  class SyncInteractionMetrics {

    @Test
    @DisplayName("should aggregate interactions and bulk-sync metrics successfully")
    void success_aggregatesAndSyncs(VertxTestContext ctx) {
      InteractionAggregate agg1 =
          new InteractionAggregate(UUID.randomUUID(), EntityType.ASSET, 10, 3, 5);
      InteractionAggregate agg2 =
          new InteractionAggregate(UUID.randomUUID(), EntityType.AI_MODEL, 25, 2, 8);
      List<InteractionAggregate> aggregates = List.of(agg1, agg2);

      BulkSyncResult expectedResult = new BulkSyncResult(2, 2, 0, List.of());

      when(dao.aggregateInteractions())
          .thenReturn(Future.succeededFuture(aggregates));
      when(itemService.bulkSyncMetrics(aggregates))
          .thenReturn(Future.succeededFuture(expectedResult));

      Future<BulkSyncResult> future = service.syncInteractionMetrics();

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getSuccessful()).isEqualTo(2);
            assertThat(result.getFailed()).isEqualTo(0);
            assertThat(result.getFailures()).isEmpty();

            verify(dao).aggregateInteractions();
            verify(itemService).bulkSyncMetrics(aggregates);
          });
    }

    @Test
    @DisplayName("should propagate failure when aggregateInteractions fails")
    void aggregateFails_propagatesError(VertxTestContext ctx) {
      when(dao.aggregateInteractions())
          .thenReturn(Future.failedFuture(new RuntimeException("Aggregate query failed")));

      Future<BulkSyncResult> future = service.syncInteractionMetrics();

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Aggregate query failed");

            verify(dao).aggregateInteractions();
            verifyNoInteractions(itemService);
          });
    }

    @Test
    @DisplayName("should propagate failure when bulkSyncMetrics fails")
    void bulkSyncFails_propagatesError(VertxTestContext ctx) {
      List<InteractionAggregate> aggregates =
          List.of(new InteractionAggregate(UUID.randomUUID(), EntityType.ASSET, 5, 1, 2));

      when(dao.aggregateInteractions())
          .thenReturn(Future.succeededFuture(aggregates));
      when(itemService.bulkSyncMetrics(aggregates))
          .thenReturn(Future.failedFuture(new RuntimeException("Bulk sync failed")));

      Future<BulkSyncResult> future = service.syncInteractionMetrics();

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Bulk sync failed");

            verify(dao).aggregateInteractions();
            verify(itemService).bulkSyncMetrics(aggregates);
          });
    }

    @Test
    @DisplayName("should handle empty aggregates list")
    void emptyAggregates_syncsWithEmptyList(VertxTestContext ctx) {
      List<InteractionAggregate> emptyAggregates = List.of();
      BulkSyncResult expectedResult = new BulkSyncResult(0, 0, 0, List.of());

      when(dao.aggregateInteractions())
          .thenReturn(Future.succeededFuture(emptyAggregates));
      when(itemService.bulkSyncMetrics(emptyAggregates))
          .thenReturn(Future.succeededFuture(expectedResult));

      Future<BulkSyncResult> future = service.syncInteractionMetrics();

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getTotal()).isEqualTo(0);

            verify(dao).aggregateInteractions();
            verify(itemService).bulkSyncMetrics(emptyAggregates);
          });
    }
  }
}
