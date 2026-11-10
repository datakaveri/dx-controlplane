package org.cdpg.dx.scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import org.cdpg.dx.database.postgres.models.DeleteQuery;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.databroker.util.PermissionOpType;
import org.cdpg.dx.databroker.util.Vhosts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("SchedulerService Tests")
class SchedulerServiceTest {

  @Mock private PostgresService postgresService;
  @Mock private DataBrokerService dataBrokerService;

  @Nested
  @DisplayName("checkSubscriptionStatus")
  class CheckSubscriptionStatus {

    @Test
    @DisplayName("should find expired subscriptions, delete queue, and update permissions")
    void checkSubscriptionStatus_findsExpired(Vertx vertx, VertxTestContext ctx) {
      String subId = UUID.randomUUID().toString();
      String queueName = "user1/test-queue";
      String userId = "user1";

      JsonArray expiredRows =
          new JsonArray()
              .add(
                  new JsonObject()
                      .put("id", subId)
                      .put("queue_name", queueName)
                      .put("user_id", userId)
                      .put("entityId", UUID.randomUUID().toString()));

      QueryResult selectResult = new QueryResult();
      selectResult.setRows(expiredRows);

      QueryResult deleteResult = new QueryResult();
      deleteResult.setRowsAffected(true);

      // Mock the select query for expired subscriptions (called immediately by constructor)
      when(postgresService.select(any(SelectQuery.class), eq(false)))
          .thenReturn(Future.succeededFuture(selectResult));
      when(dataBrokerService.deleteQueue(eq(queueName), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());
      when(dataBrokerService.updatePermission(
              eq(userId), eq(queueName), eq(PermissionOpType.DELETE_READ), eq(Vhosts.IUDX_PROD)))
          .thenReturn(Future.succeededFuture());
      when(postgresService.delete(any(DeleteQuery.class)))
          .thenReturn(Future.succeededFuture(deleteResult));

      // Constructor triggers checkSubscriptionStatus immediately
      // Use a very long interval so the periodic timer does not fire during the test
      new SchedulerServiceImpl(vertx, postgresService, dataBrokerService, 999999);

      // Use timeout-based verification because the constructor triggers async work
      verify(postgresService, timeout(5000)).select(any(SelectQuery.class), eq(false));
      verify(dataBrokerService, timeout(5000)).deleteQueue(queueName, Vhosts.IUDX_PROD);
      verify(dataBrokerService, timeout(5000))
          .updatePermission(userId, queueName, PermissionOpType.DELETE_READ, Vhosts.IUDX_PROD);
      verify(postgresService, timeout(5000)).delete(any(DeleteQuery.class));

      ctx.completeNow();
    }

    @Test
    @DisplayName("should handle no expired subscriptions gracefully")
    void checkSubscriptionStatus_noExpired(Vertx vertx, VertxTestContext ctx) {
      JsonArray emptyRows = new JsonArray();

      QueryResult selectResult = new QueryResult();
      selectResult.setRows(emptyRows);

      when(postgresService.select(any(SelectQuery.class), eq(false)))
          .thenReturn(Future.succeededFuture(selectResult));

      // Constructor triggers checkSubscriptionStatus immediately
      new SchedulerServiceImpl(vertx, postgresService, dataBrokerService, 999999);

      // Verify select was called but no delete operations occurred
      verify(postgresService, timeout(5000)).select(any(SelectQuery.class), eq(false));

      // Give time for any async operations, then verify no deletes happened
      vertx.setTimer(
          1000,
          id -> {
            verify(dataBrokerService, never()).deleteQueue(any(), any());
            verify(postgresService, never()).delete(any(DeleteQuery.class));
            ctx.completeNow();
          });
    }
  }
}
