package org.cdpg.dx.aaa.health.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.cdpg.dx.aaa.health.service.impl.HealthServiceImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("HealthService Tests")
class HealthServiceTest {

  @Mock private PostgresService postgresService;

  private HealthServiceImpl healthService;

  @BeforeEach
  void setUp(Vertx vertx) {
    healthService = new HealthServiceImpl(vertx, postgresService);
  }

  @Nested
  @DisplayName("checkLiveness")
  class CheckLiveness {

    @Test
    @DisplayName("should succeed when event loop is responsive")
    void checkLiveness_success(VertxTestContext ctx) {
      Future<Void> future = healthService.checkLiveness();

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNull();
          });
    }
  }

  @Nested
  @DisplayName("checkReadiness")
  class CheckReadiness {

    @Test
    @DisplayName("should succeed when database ping returns true")
    void checkReadiness_success(VertxTestContext ctx) {
      when(postgresService.ping()).thenReturn(Future.succeededFuture(true));

      Future<Void> future = healthService.checkReadiness();

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNull();
            verify(postgresService).ping();
          });
    }

    @Test
    @DisplayName("should fail when database ping returns false")
    void checkReadiness_pingReturnsFalse(VertxTestContext ctx) {
      when(postgresService.ping()).thenReturn(Future.succeededFuture(false));

      Future<Void> future = healthService.checkReadiness();

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).isEqualTo("DB not ready");
            verify(postgresService).ping();
          });
    }

    @Test
    @DisplayName("should fail when database ping fails with exception")
    void checkReadiness_pingFails(VertxTestContext ctx) {
      RuntimeException dbException = new RuntimeException("Connection refused");
      when(postgresService.ping()).thenReturn(Future.failedFuture(dbException));

      Future<Void> future = healthService.checkReadiness();

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Connection refused");
            verify(postgresService).ping();
          });
    }
  }
}
