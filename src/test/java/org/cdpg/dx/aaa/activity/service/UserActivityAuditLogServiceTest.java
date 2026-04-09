package org.cdpg.dx.aaa.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.activity.dao.UserActivityLogDao;
import org.cdpg.dx.aaa.activity.service.impl.UserActivityAuditLogServiceImpl;
import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
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

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("UserActivityAuditLogServiceImpl Tests")
class UserActivityAuditLogServiceTest {

  @Mock private UserActivityLogDao activityLogDAO;

  private UserActivityAuditLogServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new UserActivityAuditLogServiceImpl(activityLogDAO);
  }

  private static ActivityAuditLogEntity anActivityLogEntity() {
    ActivityAuditLogEntity entity = new ActivityAuditLogEntity();
    entity.setId(UUID.randomUUID());
    entity.setUserId(UUID.randomUUID());
    entity.setUserName("Test User");
    entity.setApi("/api/test");
    entity.setMethod("GET");
    entity.setAction("READ");
    entity.setLogType("access");
    return entity;
  }

  // ---------------------------------------------------------------------------
  // 1. insertUserActivityLogIntoDb
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("insertUserActivityLogIntoDb")
  class InsertUserActivityLogIntoDb {

    @Test
    @DisplayName("should insert activity log successfully")
    void success(VertxTestContext ctx) {
      ActivityAuditLogEntity entity = anActivityLogEntity();

      when(activityLogDAO.createActivityLog(entity))
          .thenReturn(Future.succeededFuture(entity));

      Future<Void> future = service.insertUserActivityLogIntoDb(entity);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNull();
            verify(activityLogDAO).createActivityLog(entity);
          });
    }

    @Test
    @DisplayName("should fail when DAO createActivityLog fails")
    void fail_daoError(VertxTestContext ctx) {
      ActivityAuditLogEntity entity = anActivityLogEntity();

      when(activityLogDAO.createActivityLog(entity))
          .thenReturn(Future.failedFuture(new RuntimeException("DB insert failed")));

      Future<Void> future = service.insertUserActivityLogIntoDb(entity);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).contains("DB insert failed");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 2. getUserActivityLogForConsumer
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getUserActivityLogForConsumer")
  class GetUserActivityLogForConsumer {

    @Test
    @DisplayName("should return paginated activity logs for consumer")
    void success(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), List.of(), List.of());

      ActivityAuditLogEntity log1 = anActivityLogEntity();
      ActivityAuditLogEntity log2 = anActivityLogEntity();
      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 2);
      PaginatedResult<ActivityAuditLogEntity> paginatedResult =
          new PaginatedResult<>(paginationInfo, List.of(log1, log2));

      when(activityLogDAO.getAllWithFilters(request))
          .thenReturn(Future.succeededFuture(paginatedResult));

      Future<PaginatedResult<ActivityAuditLogEntity>> future =
          service.getUserActivityLogForConsumer(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.data()).hasSize(2);
            assertThat(result.paginationInfo().getTotalCount()).isEqualTo(2);
            verify(activityLogDAO).getAllWithFilters(request);
          });
    }

    @Test
    @DisplayName("should fail when DAO getAllWithFilters fails")
    void fail_daoError(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), List.of(), List.of());

      when(activityLogDAO.getAllWithFilters(request))
          .thenReturn(Future.failedFuture(new RuntimeException("DB query failed")));

      Future<PaginatedResult<ActivityAuditLogEntity>> future =
          service.getUserActivityLogForConsumer(request);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).contains("DB query failed");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 3. getAllActivityLogsForAdmin
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getAllActivityLogsForAdmin")
  class GetAllActivityLogsForAdmin {

    @Test
    @DisplayName("should return paginated activity logs for admin")
    void success(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 20, Map.of(), List.of(), List.of());

      ActivityAuditLogEntity log1 = anActivityLogEntity();
      PaginationInfo paginationInfo = PaginationInfo.from(1, 20, 1);
      PaginatedResult<ActivityAuditLogEntity> paginatedResult =
          new PaginatedResult<>(paginationInfo, List.of(log1));

      when(activityLogDAO.getAllWithFilters(request))
          .thenReturn(Future.succeededFuture(paginatedResult));

      Future<PaginatedResult<ActivityAuditLogEntity>> future =
          service.getAllActivityLogsForAdmin(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.data()).hasSize(1);
            assertThat(result.paginationInfo().getTotalCount()).isEqualTo(1);
            verify(activityLogDAO).getAllWithFilters(request);
          });
    }

    @Test
    @DisplayName("should fail when DAO getAllWithFilters fails")
    void fail_daoError(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 20, Map.of(), List.of(), List.of());

      when(activityLogDAO.getAllWithFilters(request))
          .thenReturn(Future.failedFuture(new RuntimeException("Admin query failed")));

      Future<PaginatedResult<ActivityAuditLogEntity>> future =
          service.getAllActivityLogsForAdmin(request);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).contains("Admin query failed");
          });
    }
  }
}
