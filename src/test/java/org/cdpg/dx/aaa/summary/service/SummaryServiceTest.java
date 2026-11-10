package org.cdpg.dx.aaa.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import org.cdpg.dx.aaa.summary.dao.UsageSummaryDao;
import org.cdpg.dx.aaa.summary.model.UsageSummary;
import org.cdpg.dx.aaa.summary.service.impl.SummaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("SummaryService Tests")
class SummaryServiceTest {

  @Mock private UsageSummaryDao usageSummaryDao;

  private SummaryServiceImpl summaryService;

  @BeforeEach
  void setUp() {
    summaryService = new SummaryServiceImpl(usageSummaryDao);
  }

  @Nested
  @DisplayName("getUsageSummaryForMainDashboard")
  class GetUsageSummaryForMainDashboard {

    @Test
    @DisplayName("should return list of usage summaries on success")
    void success_returnsList(VertxTestContext ctx) {
      UsageSummary entry1 = new UsageSummary("Total Datasets", 42L, 1024L);
      UsageSummary entry2 = new UsageSummary("Total APIs", 15L, 512L);
      List<UsageSummary> expected = List.of(entry1, entry2);

      when(usageSummaryDao.fetchAllUsageSummaries())
          .thenReturn(Future.succeededFuture(expected));

      Future<List<UsageSummary>> future = summaryService.getUsageSummaryForMainDashboard();

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result.get(0).description()).isEqualTo("Total Datasets");
            assertThat(result.get(0).count()).isEqualTo(42L);
            assertThat(result.get(0).size()).isEqualTo(1024L);
            assertThat(result.get(1).description()).isEqualTo("Total APIs");
            assertThat(result.get(1).count()).isEqualTo(15L);
            assertThat(result.get(1).size()).isEqualTo(512L);
            verify(usageSummaryDao).fetchAllUsageSummaries();
          });
    }

    @Test
    @DisplayName("should return empty list when no summaries exist")
    void success_emptyResults(VertxTestContext ctx) {
      when(usageSummaryDao.fetchAllUsageSummaries())
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<List<UsageSummary>> future = summaryService.getUsageSummaryForMainDashboard();

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
            verify(usageSummaryDao).fetchAllUsageSummaries();
          });
    }

    @Test
    @DisplayName("should propagate failure when DAO fails")
    void failure_daoException(VertxTestContext ctx) {
      RuntimeException daoError = new RuntimeException("Database connection failed");

      when(usageSummaryDao.fetchAllUsageSummaries())
          .thenReturn(Future.failedFuture(daoError));

      Future<List<UsageSummary>> future = summaryService.getUsageSummaryForMainDashboard();

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Database connection failed");
            verify(usageSummaryDao).fetchAllUsageSummaries();
          });
    }
  }
}
