package org.cdpg.dx.aaa.appCredentials.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.appCredentials.dao.AppConstraintsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.AppCredentialsDAO;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentialResponse;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.impl.AppCredentialsServiceImpl;
import org.cdpg.dx.aaa.delegation.DelegationValidator;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.databroker.service.DataBrokerService;
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
@DisplayName("AppCredentialsService Tests")
class AppCredentialsServiceTest {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  @Mock private AppCredentialsDAO appCredentialsDAO;
  @Mock private AppConstraintsDAO appConstraintsDAO;
  @Mock private DelegationValidator delegationValidator;
  @Mock private DataBrokerService dataBrokerService;

  private AppCredentialsServiceImpl appCredentialsService;

  @BeforeEach
  void setUp() {
    appCredentialsService =
        new AppCredentialsServiceImpl(delegationValidator, appCredentialsDAO, appConstraintsDAO, dataBrokerService, "revoked-appid");
  }

  private AppCredentials buildAppCredentials(UUID appId, UUID userId) {
    LocalDateTime expiry = LocalDateTime.now().plusDays(30);
    return new AppCredentials(
        appId,
        userId,
        "hashed-secret",
        expiry.format(FORMATTER),
        "active",
        null,
        LocalDateTime.now().format(FORMATTER),
        null,
        null);
  }

  @Nested
  @DisplayName("createApp")
  class CreateApp {

    @Test
    @DisplayName("should create app with generated secret and hashed storage")
    void createApp_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID appId = UUID.randomUUID();
      LocalDateTime expiry = LocalDateTime.now().plusDays(30);

      AppCredentials savedApp = buildAppCredentials(appId, userId);

      JsonObject body =
          new JsonObject()
              .put("user_id", userId.toString())
              .put("expiry_at", expiry.format(FORMATTER))
              .put("status", "active");

      when(appCredentialsDAO.create(any(AppCredentials.class)))
          .thenReturn(Future.succeededFuture(savedApp));
      when(appConstraintsDAO.create(any(AppConstraints.class)))
          .thenReturn(Future.succeededFuture(null));

      Future<AppCredentials> future = appCredentialsService.createApp(body);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.appId()).isEqualTo(appId);
            assertThat(result.userId()).isEqualTo(userId);
            // The returned secret should be the plaintext (not the hashed one)
            assertThat(result.appSecret()).isNotNull();
            assertThat(result.appSecret()).isNotEqualTo("hashed-secret");
          });
    }
  }

  @Nested
  @DisplayName("getApp")
  class GetApps {

    @Test
    @DisplayName("should return paginated apps")
    void getApps_success(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      AppCredentials app = buildAppCredentials(appId, userId);
      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 1);
      PaginatedResult<AppCredentials> paginatedResult =
          new PaginatedResult<>(paginationInfo, List.of(app));
      PaginatedRequest paginatedRequest = new PaginatedRequest(1, 10, Map.of(), null, null);

      when(appCredentialsDAO.getAllWithFilters(any(PaginatedRequest.class)))
          .thenReturn(Future.succeededFuture(paginatedResult));

      Future<PaginatedResult<AppCredentialResponse>> future =
          appCredentialsService.getApp(paginatedRequest);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).appId()).isEqualTo(appId);
            assertThat(result.data().get(0).userId()).isEqualTo(userId);
          });
    }
  }

  @Nested
  @DisplayName("getAppById")
  class GetAppById {

    @Test
    @DisplayName("should return app when found")
    void getAppById_success(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      AppCredentials app = buildAppCredentials(appId, userId);

      when(appCredentialsDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(app)));

      Future<AppCredentials> future = appCredentialsService.getAppById(appId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.appId()).isEqualTo(appId);
          });
    }

    @Test
    @DisplayName("should fail when app not found")
    void getAppById_notFound(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();

      when(appCredentialsDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of()));

      Future<AppCredentials> future = appCredentialsService.getAppById(appId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
          });
    }
  }

  @Nested
  @DisplayName("deleteApp")
  class DeleteApp {

    @Test
    @DisplayName("should delete app successfully")
    void deleteApp_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID appId = UUID.randomUUID();

      AppCredentials app = buildAppCredentials(appId, userId);

      when(appCredentialsDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(app)));
      when(appCredentialsDAO.delete(appId)).thenReturn(Future.succeededFuture(true));

      Future<Boolean> future = appCredentialsService.deleteApp(userId, appId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
            verify(appCredentialsDAO).delete(appId);
          });
    }
  }

  @Nested
  @DisplayName("changeAppStatus")
  class UpdateAppStatus {

    @Test
    @DisplayName("should update app status successfully")
    void updateAppStatus_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID appId = UUID.randomUUID();
      String newStatus = "revoked";

      AppCredentials updatedApp = buildAppCredentials(appId, userId);

      when(appCredentialsDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(updatedApp));

      Future<Boolean> future = appCredentialsService.changeAppStatus(userId, appId, newStatus);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
          });
    }
  }
}
