package org.cdpg.dx.aaa.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Map;
import org.cdpg.dx.aaa.leaderboard.dao.LeaderboardDao;
import org.cdpg.dx.aaa.leaderboard.model.AssetLeaderboardEntry;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardResponse;
import org.cdpg.dx.aaa.leaderboard.model.OrganizationLeaderboardEntry;
import org.cdpg.dx.aaa.leaderboard.model.ProviderLeaderboardEntry;
import org.cdpg.dx.aaa.leaderboard.service.impl.LeaderboardServiceImpl;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("LeaderboardService Tests")
class LeaderboardServiceTest {

  @Mock private LeaderboardDao leaderboardDao;

  private LeaderboardServiceImpl leaderboardService;

  @BeforeEach
  void setUp() {
    leaderboardService = new LeaderboardServiceImpl(leaderboardDao);
  }

  @Nested
  @DisplayName("getAssetLeaderboard")
  class GetAssetLeaderboard {

    @Test
    @DisplayName("should return asset leaderboard with pagination")
    void getAssetLeaderboard_success(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), null, null);

      AssetLeaderboardEntry entry =
          new AssetLeaderboardEntry(
              1,
              "asset-id-1",
              "Test Asset",
              "dataset",
              "A test asset",
              "open",
              "provider-1",
              "Provider Name",
              "org-1",
              "Org Name",
              "Private",
              100L,
              50L,
              200L);

      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 1);
      LeaderboardResponse<AssetLeaderboardEntry> expectedResponse =
          new LeaderboardResponse<>(List.of(entry), paginationInfo);

      when(leaderboardDao.fetchAssetLeaderboard(any(PaginatedRequest.class)))
          .thenReturn(Future.succeededFuture(expectedResponse));

      Future<LeaderboardResponse<AssetLeaderboardEntry>> future =
          leaderboardService.getAssetLeaderboard(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).name()).isEqualTo("Test Asset");
            assertThat(result.getData().get(0).rank()).isEqualTo(1);
            assertThat(result.getData().get(0).downloads()).isEqualTo(100L);
            assertThat(result.getPaginationInfo()).isNotNull();
            assertThat(result.getPaginationInfo().getTotalCount()).isEqualTo(1);
            verify(leaderboardDao).fetchAssetLeaderboard(request);
          });
    }
  }

  @Nested
  @DisplayName("getProviderLeaderboard")
  class GetProviderLeaderboard {

    @Test
    @DisplayName("should return provider leaderboard with pagination")
    void getProviderLeaderboard_success(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), null, null);

      ProviderLeaderboardEntry entry =
          new ProviderLeaderboardEntry(
              1,
              "provider-id-1",
              "Test Provider",
              "org-1",
              "Org Name",
              "Private",
              10L,
              5L,
              3L,
              18L,
              200L,
              50L);

      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 1);
      LeaderboardResponse<ProviderLeaderboardEntry> expectedResponse =
          new LeaderboardResponse<>(List.of(entry), paginationInfo);

      when(leaderboardDao.fetchProviderLeaderboard(any(PaginatedRequest.class)))
          .thenReturn(Future.succeededFuture(expectedResponse));

      Future<LeaderboardResponse<ProviderLeaderboardEntry>> future =
          leaderboardService.getProviderLeaderboard(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).username()).isEqualTo("Test Provider");
            assertThat(result.getData().get(0).rank()).isEqualTo(1);
            assertThat(result.getData().get(0).totalPublished()).isEqualTo(18L);
            assertThat(result.getPaginationInfo()).isNotNull();
            verify(leaderboardDao).fetchProviderLeaderboard(request);
          });
    }
  }

  @Nested
  @DisplayName("getOrgLeaderboard")
  class GetOrganizationLeaderboard {

    @Test
    @DisplayName("should return organization leaderboard with pagination")
    void getOrganizationLeaderboard_success(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, Map.of(), null, null);

      OrganizationLeaderboardEntry entry =
          new OrganizationLeaderboardEntry(
              1,
              "org-id-1",
              "Test Org",
              "Private",
              25L,
              10L,
              5L,
              3L,
              18L,
              300L,
              75L);

      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 1);
      LeaderboardResponse<OrganizationLeaderboardEntry> expectedResponse =
          new LeaderboardResponse<>(List.of(entry), paginationInfo);

      when(leaderboardDao.fetchOrgLeaderboard(any(PaginatedRequest.class)))
          .thenReturn(Future.succeededFuture(expectedResponse));

      Future<LeaderboardResponse<OrganizationLeaderboardEntry>> future =
          leaderboardService.getOrgLeaderboard(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).organizationName()).isEqualTo("Test Org");
            assertThat(result.getData().get(0).rank()).isEqualTo(1);
            assertThat(result.getData().get(0).members()).isEqualTo(25L);
            assertThat(result.getData().get(0).totalPublished()).isEqualTo(18L);
            assertThat(result.getPaginationInfo()).isNotNull();
            assertThat(result.getPaginationInfo().getTotalCount()).isEqualTo(1);
            verify(leaderboardDao).fetchOrgLeaderboard(request);
          });
    }
  }
}
