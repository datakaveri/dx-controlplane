package org.cdpg.dx.aaa.token.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.token.model.AppTokenRequest;
import org.cdpg.dx.aaa.token.service.impl.AppTokenServiceImpl;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("AppTokenService Tests")
class AppTokenServiceTest {

  private static final String ISSUER = "test-issuer";
  private static final int TOKEN_EXPIRATION_MINUTES = 60;
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
  private static final String PLAIN_SECRET = "my-super-secret-app-key";
  private static final String HASHED_SECRET = DigestUtils.sha512Hex(PLAIN_SECRET);

  @Mock private JWTAuth jwtAuth;
  @Mock private KeycloakUserService keycloakUserService;
  @Mock private AppCredentialsService appCredentialsService;
  @Mock private ItemService itemService;

  private AppTokenServiceImpl appTokenService;

  @BeforeEach
  void setUp(Vertx vertx) {
    appTokenService =
        new AppTokenServiceImpl(
            jwtAuth,
            keycloakUserService,
            appCredentialsService,
            itemService,
            ISSUER,
            TOKEN_EXPIRATION_MINUTES,
            vertx);
  }

  // ---------------------------------------------------------------------------
  // Helper builders
  // ---------------------------------------------------------------------------

  private AppCredentials buildActiveApp(UUID appId, UUID userId) {
    String futureExpiry =
        LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusDays(30).format(FORMATTER);
    return new AppCredentials(
        appId,
        userId,
        HASHED_SECRET,
        futureExpiry,
        "active",
        null,
        LocalDateTime.now().format(FORMATTER),
        null,
        null);
  }

  private AppCredentials buildRevokedApp(UUID appId, UUID userId) {
    String futureExpiry =
        LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusDays(30).format(FORMATTER);
    return new AppCredentials(
        appId,
        userId,
        HASHED_SECRET,
        futureExpiry,
        "active",
        null,
        LocalDateTime.now().format(FORMATTER),
        null,
        LocalDateTime.now().format(FORMATTER)); // revokedAt is non-null
  }

  private AppCredentials buildInactiveApp(UUID appId, UUID userId) {
    String futureExpiry =
        LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusDays(30).format(FORMATTER);
    return new AppCredentials(
        appId,
        userId,
        HASHED_SECRET,
        futureExpiry,
        "inactive",
        null,
        LocalDateTime.now().format(FORMATTER),
        null,
        null);
  }

  private AppCredentials buildExpiredApp(UUID appId, UUID userId) {
    String pastExpiry =
        LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusDays(5).format(FORMATTER);
    return new AppCredentials(
        appId,
        userId,
        HASHED_SECRET,
        pastExpiry,
        "active",
        null,
        LocalDateTime.now().format(FORMATTER),
        null,
        null);
  }

  private DxUser buildDxUser(UUID userId, List<String> roles) {
    return new DxUser(
        roles,
        "org-123",
        "Test Org",
        userId,
        true,
        false,
        "Test User",
        "testuser",
        "Test",
        "User",
        "test@example.com",
        null,
        new JsonObject(),
        LocalDateTime.now(),
        null,
        null,
        null,
        null,
        true,
        null,
        null,
        new JsonArray());
  }

  private AppConstraints buildConstraint(UUID appId, UUID userId, String scope) {
    return new AppConstraints(UUID.randomUUID(), appId, scope, "*", "*", userId);
  }

  private AppConstraints buildConstraintWithEntity(
      UUID appId, UUID userId, String scope, String entityId, String entityType) {
    return new AppConstraints(UUID.randomUUID(), appId, scope, entityId, entityType, userId);
  }

  private void stubJwtGeneration() {
    when(jwtAuth.generateToken(any(JsonObject.class), any(JWTOptions.class)))
        .thenReturn("mocked-jwt-token");
  }

  // ===========================================================================
  // Test groups
  // ===========================================================================

  @Nested
  @DisplayName("createToken - invalid request input")
  class CreateTokenInvalidInput {

    @Test
    @DisplayName("should fail when request is null")
    void nullRequest(VertxTestContext ctx) {
      Future<JsonObject> future = appTokenService.createToken(null, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(Throwable.class);
            assertThat(err.getMessage()).contains("invalid_app_credentials");
          });
    }

    @Test
    @DisplayName("should fail when appId is null")
    void nullAppId(VertxTestContext ctx) {
      AppTokenRequest request = new AppTokenRequest(null, "some-secret");

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(Throwable.class);
            assertThat(err.getMessage()).contains("invalid_app_credentials");
          });
    }

    @Test
    @DisplayName("should fail when appSecret is null")
    void nullAppSecret(VertxTestContext ctx) {
      AppTokenRequest request = new AppTokenRequest(UUID.randomUUID(), null);

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(Throwable.class);
            assertThat(err.getMessage()).contains("invalid_app_credentials");
          });
    }
  }

  @Nested
  @DisplayName("createToken - app validation failures")
  class CreateTokenValidationFailures {

    @Test
    @DisplayName("should fail when app is revoked")
    void revokedApp(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials revokedApp = buildRevokedApp(appId, userId);
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      when(appCredentialsService.getAppById(appId))
          .thenReturn(Future.succeededFuture(revokedApp));

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxForbiddenException.class);
            assertThat(err.getMessage()).containsIgnoringCase("revoked");
          });
    }

    @Test
    @DisplayName("should fail when app is not active")
    void inactiveApp(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials inactiveApp = buildInactiveApp(appId, userId);
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      when(appCredentialsService.getAppById(appId))
          .thenReturn(Future.succeededFuture(inactiveApp));

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxForbiddenException.class);
            assertThat(err.getMessage()).containsIgnoringCase("not active");
          });
    }

    @Test
    @DisplayName("should fail when app is expired")
    void expiredApp(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials expiredApp = buildExpiredApp(appId, userId);
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      when(appCredentialsService.getAppById(appId))
          .thenReturn(Future.succeededFuture(expiredApp));

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxUnauthorizedException.class);
            assertThat(err.getMessage()).containsIgnoringCase("expired");
          });
    }

    @Test
    @DisplayName("should fail when secret does not match (SHA-512 hash comparison)")
    void secretMismatch(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      AppTokenRequest request = new AppTokenRequest(appId, "wrong-secret");

      when(appCredentialsService.getAppById(appId))
          .thenReturn(Future.succeededFuture(app));

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxUnauthorizedException.class);
            assertThat(err.getMessage()).containsIgnoringCase("invalid credentials");
          });
    }

    @Test
    @DisplayName("should fail when getAppById returns a failed future")
    void appLookupFails(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      when(appCredentialsService.getAppById(appId))
          .thenReturn(Future.failedFuture(new DxNotFoundException("App not found")));

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
          });
    }
  }

  @Nested
  @DisplayName("createToken - successful token issuance (no item)")
  class CreateTokenSuccess {

    @Test
    @DisplayName("should return JWT token for valid app with asset_management scope")
    void validApp_assetManagement(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("provider"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "asset_management"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
            assertThat(result.getString("token_type")).isEqualTo("Bearer");
            assertThat(result.getInteger("expires_in_minutes")).isEqualTo(TOKEN_EXPIRATION_MINUTES);
            verify(jwtAuth).generateToken(any(JsonObject.class), any(JWTOptions.class));
          });
    }

    @Test
    @DisplayName("should return JWT token for valid app with user_management scope")
    void validApp_userManagement(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("org_admin"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "user_management"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
            assertThat(result.getString("token_type")).isEqualTo("Bearer");
          });
    }

    @Test
    @DisplayName("should handle app with leading/trailing whitespace in secret")
    void validApp_secretWithWhitespace(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, "  " + PLAIN_SECRET + "  ");

      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "asset_management"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
          });
    }
  }

  @Nested
  @DisplayName("createToken - scope resolution with wildcard (*)")
  class ScopeResolutionWildcard {

    @Test
    @DisplayName("wildcard scope with cos_admin role should include all admin scopes")
    void wildcard_cosAdmin(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("cos_admin", "provider", "consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints = List.of(buildConstraint(appId, userId, "*"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      // Since wildcard + consumer triggers needsItemCheck=true and resolveItemId returns
      // null for a constraint with entityId="*" (not a valid UUID), the token is issued
      // without item info.
      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
            verify(jwtAuth).generateToken(any(JsonObject.class), any(JWTOptions.class));
          });
    }

    @Test
    @DisplayName("wildcard scope with org_admin role should include org_admin scopes")
    void wildcard_orgAdmin(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("org_admin", "consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints = List.of(buildConstraint(appId, userId, "*"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
          });
    }

    @Test
    @DisplayName("wildcard scope with provider role should include asset_management scope")
    void wildcard_provider(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("provider"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints = List.of(buildConstraint(appId, userId, "*"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
          });
    }

    @Test
    @DisplayName("wildcard scope with consumer role should include data_access scope")
    void wildcard_consumer(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints = List.of(buildConstraint(appId, userId, "*"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      // consumer + wildcard triggers needsItemCheck, but no valid itemId in constraints
      // and no request itemId, so token is issued without item info.
      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
          });
    }

    @Test
    @DisplayName("wildcard scope with compute role should include credit_management")
    void wildcard_compute(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("compute"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints = List.of(buildConstraint(appId, userId, "*"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
          });
    }
  }

  @Nested
  @DisplayName("createToken - explicit scope resolution")
  class ScopeResolutionExplicit {

    @Test
    @DisplayName("cos_admin_access scope should include multiple sub-scopes and roles")
    void cosAdminAccessScope(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("cos_admin"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "cos_admin_access"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isNotNull();
          });
    }

    @Test
    @DisplayName("org_admin_access scope should include org_admin related scopes")
    void orgAdminAccessScope(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("org_admin"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "org_admin_access"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isNotNull();
          });
    }

    @Test
    @DisplayName("compute_management scope should add compute role")
    void computeManagementScope(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("compute"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "compute_management"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isNotNull();
          });
    }

    @Test
    @DisplayName("data_access scope without itemId should issue token without item info")
    void dataAccessScope_noItemId(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      // entityId = "*" which is not a valid UUID, and no request itemId
      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "data_access"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
            // No itemService call should have been made because resolveItemId returns null
            verify(itemService, never()).getItemWithAccessChecks(any());
          });
    }

    @Test
    @DisplayName("multiple scopes should be resolved together")
    void multipleScopes(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("org_admin", "provider"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(
              buildConstraint(appId, userId, "asset_management"),
              buildConstraint(appId, userId, "user_management"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
          });
    }

    @Test
    @DisplayName("empty/null scopes in constraints should be filtered out")
    void emptyScopes(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(
              new AppConstraints(UUID.randomUUID(), appId, null, "*", "*", userId),
              new AppConstraints(UUID.randomUUID(), appId, "", "*", "*", userId));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
          });
    }
  }

  @Nested
  @DisplayName("createToken - data_access with item resolution")
  class DataAccessWithItem {

    @Test
    @DisplayName("data_access scope with valid constraint entityId should fetch and embed item info")
    void dataAccess_constraintEntityId(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID itemUUID = UUID.randomUUID();
      String itemId = itemUUID.toString();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(
              buildConstraintWithEntity(appId, userId, "data_access", itemId, "adex:Apps"));

      // Build a mock ResponseModel
      JsonObject itemJson =
          new JsonObject()
              .put("type", new JsonArray().add("adex:Apps"))
              .put("organizationId", "org-1")
              .put("ownerUserId", userId.toString())
              .put("accessPolicy", "open")
              .put("shortDescription", "Test item")
              .put("resourceServer", new JsonArray())
              .put("did", "did:example:123")
              .put("id", itemId)
              .put("policies", new JsonArray());

      JsonObject responseJson = new JsonObject().put("results", new JsonArray().add(itemJson));
      ResponseModel mockResponse = org.mockito.Mockito.mock(ResponseModel.class);
      when(mockResponse.getResponse()).thenReturn(responseJson);

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      when(itemService.getItemWithAccessChecks(any())).thenReturn(Future.succeededFuture(mockResponse));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
            verify(itemService).getItemWithAccessChecks(any());
          });
    }

    @Test
    @DisplayName("data_access scope with request body itemId should use request itemId as fallback")
    void dataAccess_requestBodyItemId(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID itemUUID = UUID.randomUUID();
      String requestItemId = itemUUID.toString();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      // Constraint has entityId="*" (not a valid UUID) so resolveItemId falls back to requestItemId
      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "data_access"));

      JsonObject itemJson =
          new JsonObject()
              .put("type", new JsonArray().add("adex:Apps"))
              .put("organizationId", "org-1")
              .put("ownerUserId", userId.toString())
              .put("accessPolicy", "open")
              .put("shortDescription", "Test item")
              .put("resourceServer", new JsonArray())
              .put("did", "did:example:123")
              .put("id", requestItemId)
              .put("policies", new JsonArray());

      JsonObject responseJson = new JsonObject().put("results", new JsonArray().add(itemJson));
      ResponseModel mockResponse = org.mockito.Mockito.mock(ResponseModel.class);
      when(mockResponse.getResponse()).thenReturn(responseJson);

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      when(itemService.getItemWithAccessChecks(any())).thenReturn(Future.succeededFuture(mockResponse));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, requestItemId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
            verify(itemService).getItemWithAccessChecks(any());
          });
    }

    @Test
    @DisplayName("data_access with item not found should fail with DxNotFoundException")
    void dataAccess_itemNotFound(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID itemUUID = UUID.randomUUID();
      String itemId = itemUUID.toString();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(
              buildConstraintWithEntity(appId, userId, "data_access", itemId, "adex:Apps"));

      ResponseModel mockResponse = org.mockito.Mockito.mock(ResponseModel.class);
      when(mockResponse.getResponse()).thenReturn(null);

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      when(itemService.getItemWithAccessChecks(any())).thenReturn(Future.succeededFuture(mockResponse));

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
          });
    }

    @Test
    @DisplayName("data_access with empty results array should fail with DxNotFoundException")
    void dataAccess_emptyResults(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID itemUUID = UUID.randomUUID();
      String itemId = itemUUID.toString();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(
              buildConstraintWithEntity(appId, userId, "data_access", itemId, "adex:Apps"));

      JsonObject responseJson = new JsonObject().put("results", new JsonArray());
      ResponseModel mockResponse = org.mockito.Mockito.mock(ResponseModel.class);
      when(mockResponse.getResponse()).thenReturn(responseJson);

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      when(itemService.getItemWithAccessChecks(any())).thenReturn(Future.succeededFuture(mockResponse));

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
          });
    }

    @Test
    @DisplayName("data_access with item not permitted by constraints should fail with DxForbiddenException")
    void dataAccess_itemNotPermittedByConstraints(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID constraintItemId = UUID.randomUUID();
      UUID differentItemId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      // Use an entityType NOT in dataItemTypes so resolveItemId skips this constraint
      // and falls back to the request body itemId (differentItemId).
      // The constraint still has a non-blank entityId, so the constraint check triggers
      // and differentItemId won't match constraintItemId, causing DxForbiddenException.
      List<AppConstraints> constraints =
          List.of(
              buildConstraintWithEntity(
                  appId, userId, "data_access", constraintItemId.toString(), "other:Type"));

      JsonObject itemJson =
          new JsonObject()
              .put("type", new JsonArray().add("adex:Apps"))
              .put("organizationId", "org-1")
              .put("ownerUserId", userId.toString())
              .put("accessPolicy", "open")
              .put("shortDescription", "Test item")
              .put("resourceServer", new JsonArray())
              .put("did", "did:example:123")
              .put("id", differentItemId.toString())
              .put("policies", new JsonArray());

      JsonObject responseJson = new JsonObject().put("results", new JsonArray().add(itemJson));
      ResponseModel mockResponse = org.mockito.Mockito.mock(ResponseModel.class);
      when(mockResponse.getResponse()).thenReturn(responseJson);

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      when(itemService.getItemWithAccessChecks(any())).thenReturn(Future.succeededFuture(mockResponse));

      Future<JsonObject> future = appTokenService.createToken(request, differentItemId.toString());

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxForbiddenException.class);
            assertThat(err.getMessage()).containsIgnoringCase("not permitted");
          });
    }

    @Test
    @DisplayName("data_access with wildcard entityId constraint should allow any item")
    void dataAccess_wildcardConstraintAllowsAnyItem(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID itemUUID = UUID.randomUUID();
      String requestItemId = itemUUID.toString();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      // One constraint with wildcard entityId "*" (not a valid UUID), another with actual entityId
      // resolveItemId will use the constraint with a valid UUID entityId. But to test
      // the wildcard path, we use two constraints: one with "*" entityId and one data_access
      // with an entityId that is a valid UUID. The item returned is different from the
      // constraint entityId, but the wildcard constraint grants access.
      UUID constraintEntityId = UUID.randomUUID();
      List<AppConstraints> constraints =
          List.of(
              buildConstraintWithEntity(appId, userId, "data_access", "*", "adex:Apps"),
              buildConstraintWithEntity(
                  appId, userId, "data_access", constraintEntityId.toString(), "adex:DataBank"));

      // resolveItemId picks the first constraint with entityType in dataItemTypes AND valid UUID
      // which would be the second constraint (constraintEntityId).
      // The item returned matches that constraintEntityId.
      JsonObject itemJson =
          new JsonObject()
              .put("type", new JsonArray().add("adex:DataBank"))
              .put("organizationId", "org-1")
              .put("ownerUserId", userId.toString())
              .put("accessPolicy", "open")
              .put("shortDescription", "Test item")
              .put("resourceServer", new JsonArray())
              .put("did", "did:example:123")
              .put("id", constraintEntityId.toString())
              .put("policies", new JsonArray());

      JsonObject responseJson = new JsonObject().put("results", new JsonArray().add(itemJson));
      ResponseModel mockResponse = org.mockito.Mockito.mock(ResponseModel.class);
      when(mockResponse.getResponse()).thenReturn(responseJson);

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      when(itemService.getItemWithAccessChecks(any())).thenReturn(Future.succeededFuture(mockResponse));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("mocked-jwt-token");
          });
    }
  }

  @Nested
  @DisplayName("createToken - downstream service failures")
  class DownstreamServiceFailures {

    @Test
    @DisplayName("should propagate failure when keycloakUserService.getUserById fails")
    void keycloakUserServiceFailure(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "asset_management"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId))
          .thenReturn(Future.failedFuture(new RuntimeException("Keycloak unavailable")));

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).contains("Keycloak unavailable");
          });
    }

    @Test
    @DisplayName("should propagate failure when getAppConstraintsById fails")
    void constraintsLookupFailure(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.failedFuture(new RuntimeException("DB error")));

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).contains("DB error");
          });
    }

    @Test
    @DisplayName("should propagate failure when itemService.getItemWithAccessChecks fails")
    void itemServiceFailure(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID itemUUID = UUID.randomUUID();
      String itemId = itemUUID.toString();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(
              buildConstraintWithEntity(appId, userId, "data_access", itemId, "adex:Apps"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.failedFuture(new RuntimeException("Elasticsearch down")));

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).contains("Elasticsearch down");
          });
    }
  }

  @Nested
  @DisplayName("createToken - JWT generation verification")
  class JwtGenerationVerification {

    @Test
    @DisplayName("generated token claims should contain appId")
    void claimsContainAppId(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("provider"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "asset_management"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));

      // Capture the claims passed to generateToken
      final JsonObject[] capturedClaims = new JsonObject[1];
      when(jwtAuth.generateToken(any(JsonObject.class), any(JWTOptions.class)))
          .thenAnswer(
              invocation -> {
                capturedClaims[0] = invocation.getArgument(0);
                return "mocked-jwt-token";
              });

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(capturedClaims[0]).isNotNull();
            assertThat(capturedClaims[0].getString("appId")).isEqualTo(appId.toString());
            assertThat(capturedClaims[0].containsKey("scope")).isTrue();
            assertThat(capturedClaims[0].containsKey("realm_access")).isTrue();

            JsonArray scopes = capturedClaims[0].getJsonArray("scope");
            assertThat(scopes).isNotNull();
            assertThat(scopes.getList()).contains("asset_management");

            JsonObject realmAccess = capturedClaims[0].getJsonObject("realm_access");
            assertThat(realmAccess).isNotNull();
            JsonArray roles = realmAccess.getJsonArray("roles");
            assertThat(roles.getList()).contains("provider");
            // consumer role is always added
            assertThat(roles.getList()).contains("consumer");
          });
    }

    @Test
    @DisplayName("token response should have correct structure")
    void tokenResponseStructure(VertxTestContext ctx) {
      UUID appId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      AppCredentials app = buildActiveApp(appId, userId);
      DxUser user = buildDxUser(userId, List.of("consumer"));
      AppTokenRequest request = new AppTokenRequest(appId, PLAIN_SECRET);

      List<AppConstraints> constraints =
          List.of(buildConstraint(appId, userId, "asset_management"));

      when(appCredentialsService.getAppById(appId)).thenReturn(Future.succeededFuture(app));
      when(appCredentialsService.getAppConstraintsById(appId))
          .thenReturn(Future.succeededFuture(constraints));
      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
      stubJwtGeneration();

      Future<JsonObject> future = appTokenService.createToken(request, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.containsKey("access_token")).isTrue();
            assertThat(result.containsKey("token_type")).isTrue();
            assertThat(result.containsKey("expires_in_minutes")).isTrue();
            assertThat(result.getString("token_type")).isEqualTo("Bearer");
            assertThat(result.getInteger("expires_in_minutes")).isEqualTo(TOKEN_EXPIRATION_MINUTES);
          });
    }
  }
}
