package org.cdpg.dx.aaa.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.item.service.AssetDeletionCleanupService.AssetReference;
import org.cdpg.dx.database.postgres.models.DeleteQuery;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssetDeletionCleanupServiceTest {

  private static final UUID ASSET_ID = UUID.fromString("76c57cc6-6699-4501-ae7f-17acdaede10d");

  private PostgresService postgresService;
  private Vertx vertx;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    postgresService = mock(PostgresService.class);
    vertx = mock(Vertx.class);
    // Run retry timers immediately.
    when(vertx.setTimer(anyLong(), any()))
        .thenAnswer(
            inv -> {
              ((Handler<Long>) inv.getArgument(1)).handle(0L);
              return 0L;
            });
  }

  @Test
  void deletesAssetRequestRowsForDeletedAsset() {
    when(postgresService.delete(any())).thenReturn(Future.succeededFuture(new QueryResult()));

    Future<Void> result =
        new AssetDeletionCleanupService(postgresService).cleanupAsync(vertx, ASSET_ID);

    assertThat(result.succeeded()).isTrue();
    ArgumentCaptor<DeleteQuery> captor = ArgumentCaptor.forClass(DeleteQuery.class);
    verify(postgresService).delete(captor.capture());
    assertThat(captor.getValue().getTable()).isEqualTo("asset_request");
    assertThat(captor.getValue().toSQL()).contains("asset_id");
    assertThat(captor.getValue().getQueryParams()).containsExactly(ASSET_ID.toString());
    verify(vertx, never()).setTimer(anyLong(), any());
  }

  @Test
  void retriesThenSucceeds() {
    when(postgresService.delete(any()))
        .thenReturn(Future.failedFuture("db down"))
        .thenReturn(Future.succeededFuture(new QueryResult()));

    Future<Void> result =
        new AssetDeletionCleanupService(postgresService).cleanupAsync(vertx, ASSET_ID);

    assertThat(result.succeeded()).isTrue();
    verify(postgresService, times(2)).delete(any());
  }

  @Test
  void givesUpAfterMaxAttemptsWithoutFailing() {
    when(postgresService.delete(any())).thenReturn(Future.failedFuture("db down"));

    Future<Void> result =
        new AssetDeletionCleanupService(postgresService).cleanupAsync(vertx, ASSET_ID);

    assertThat(result.succeeded()).isTrue();
    verify(postgresService, times(AssetDeletionCleanupService.MAX_ATTEMPTS)).delete(any());
  }

  @Test
  void failureInOneTableDoesNotBlockOthers() {
    when(postgresService.delete(any()))
        .thenAnswer(
            inv ->
                "bad_table".equals(((DeleteQuery) inv.getArgument(0)).getTable())
                    ? Future.failedFuture("boom")
                    : Future.succeededFuture(new QueryResult()));

    Future<Void> result =
        new AssetDeletionCleanupService(
                postgresService,
                List.of(
                    new AssetReference("bad_table", "asset_id"),
                    new AssetReference("asset_request", "asset_id")))
            .cleanupAsync(vertx, ASSET_ID);

    assertThat(result.succeeded()).isTrue();
    verify(postgresService, times(AssetDeletionCleanupService.MAX_ATTEMPTS + 1)).delete(any());
  }
}
