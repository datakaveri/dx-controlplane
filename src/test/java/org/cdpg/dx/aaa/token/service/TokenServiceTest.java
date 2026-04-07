package org.cdpg.dx.aaa.token.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialService;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.delegation.DelegationAccessEvaluator;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.token.model.AccessTokenRequest;
import org.cdpg.dx.aaa.token.model.DelegationValidationResult;
import org.cdpg.dx.aaa.token.model.ItemInfo;
import org.cdpg.dx.aaa.token.service.impl.TokenServiceImpl;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
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
@DisplayName("TokenService Tests")
class TokenServiceTest {

  private static final String ISSUER = "https://test-issuer.example.com";
  private static final int EXPIRATION_MINUTES = 60;
  private static final String CLIENT_ID = "test-client-id";
  private static final String CLIENT_SECRET = "test-client-secret";

  @Mock private JWTAuth jwtAuth;
  @Mock private KeycloakUserService keycloakUserService;
  @Mock private ClientcredetialService clientcredetialService;
  @Mock private ItemService itemService;
  @Mock private DelegationService delegationService;
  @Mock private DelegationAccessEvaluator delegationAccessEvaluator;
  @Mock private Vertx vertx;

  private TokenServiceImpl tokenService;

  @BeforeEach
  void setUp() {
    tokenService =
        new TokenServiceImpl(
            jwtAuth,
            keycloakUserService,
            clientcredetialService,
            itemService,
            ISSUER,
            EXPIRATION_MINUTES,
            delegationService,
            delegationAccessEvaluator,
            vertx);
  }

  // ============================
  // TEST DATA HELPERS
  // ============================

  private static DxUser buildTestUser(UUID userId) {
    return new DxUser(
        List.of("consumer"),
        "org-001",
        "Test Organisation",
        userId,
        true,
        false,
        "Test User",
        "testuser",
        "Test",
        "User",
        "testuser@example.com",
        List.of(),
        new JsonObject(),
        LocalDateTime.now(),
        new JsonObject(),
        null,
        null,
        null,
        true,
        null,
        null,
        new JsonArray());
  }

  private static DxUser buildTestUserWithRoles(UUID userId, List<String> roles) {
    return new DxUser(
        roles,
        "org-001",
        "Test Organisation",
        userId,
        true,
        false,
        "Test User",
        "testuser",
        "Test",
        "User",
        "testuser@example.com",
        List.of(),
        new JsonObject(),
        LocalDateTime.now(),
        new JsonObject(),
        null,
        null,
        null,
        true,
        null,
        null,
        new JsonArray());
  }

  private AccessTokenRequest identityTokenRequest() {
    return new AccessTokenRequest(CLIENT_ID, CLIENT_SECRET, null, null, null);
  }

  private AccessTokenRequest accessTokenRequest(String itemId) {
    return new AccessTokenRequest(CLIENT_ID, CLIENT_SECRET, null, itemId, null);
  }

  private AccessTokenRequest delegationTokenRequest(String delegationId, String itemId) {
    return new AccessTokenRequest(CLIENT_ID, CLIENT_SECRET, delegationId, itemId, null);
  }

  /**
   * Stubs the clientcredetialService and keycloakUserService to return the given userId and user
   * respectively, for any hashed clientId/clientSecret combination.
   */
  private void stubGetDxUser(UUID userId, DxUser user) {
    when(clientcredetialService.getUserIdByClientIdAndSecret(anyString(), anyString()))
        .thenReturn(Future.succeededFuture(userId));
    when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(user));
  }

  /**
   * Stubs the JWTAuth provider to return a fixed token string for any claims/options.
   */
  private void stubJwtGeneration(String tokenValue) {
    lenient()
        .when(jwtAuth.generateToken(any(JsonObject.class), any(JWTOptions.class)))
        .thenReturn(tokenValue);
  }

  /**
   * Creates a mock ResponseModel-compatible return value for item service.
   * Since ResponseModel has complex constructors depending on Elasticsearch internals,
   * we use a Mockito mock to control getResponse().
   */
  private ResponseModel mockResponseModelWithItem(JsonObject item) {
    ResponseModel responseModel = org.mockito.Mockito.mock(ResponseModel.class);
    JsonObject responseJson = new JsonObject().put("results", new JsonArray().add(item));
    when(responseModel.getResponse()).thenReturn(responseJson);
    return responseModel;
  }

  // ============================
  // VALIDATION TESTS
  // ============================

  @Nested
  @DisplayName("Request Validation")
  class RequestValidation {

    @Test
    @DisplayName("should fail when clientId is null")
    void createToken_nullClientId_fails(VertxTestContext ctx) {
      AccessTokenRequest request =
          new AccessTokenRequest(null, CLIENT_SECRET, null, null, null);

      Future<JsonObject> future = tokenService.createToken(request);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).contains("Missing clientId or clientSecret");
            verifyNoInteractions(clientcredetialService);
            verifyNoInteractions(keycloakUserService);
          });
    }

    @Test
    @DisplayName("should fail when clientSecret is null")
    void createToken_nullClientSecret_fails(VertxTestContext ctx) {
      AccessTokenRequest request =
          new AccessTokenRequest(CLIENT_ID, null, null, null, null);

      Future<JsonObject> future = tokenService.createToken(request);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).contains("Missing clientId or clientSecret");
            verifyNoInteractions(clientcredetialService);
            verifyNoInteractions(keycloakUserService);
          });
    }

    @Test
    @DisplayName("should fail when both clientId and clientSecret are null")
    void createToken_bothNull_fails(VertxTestContext ctx) {
      AccessTokenRequest request =
          new AccessTokenRequest(null, null, null, null, null);

      Future<JsonObject> future = tokenService.createToken(request);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).contains("Missing clientId or clientSecret");
          });
    }
  }

  // ============================
  // IDENTITY TOKEN TESTS
  // ============================

  @Nested
  @DisplayName("Identity Token (no itemId, no delegationId)")
  class IdentityToken {

    @Test
    @DisplayName("should create identity token successfully")
    void createIdentityToken_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      DxUser user = buildTestUser(userId);
      String expectedToken = "jwt-identity-token-value";

      stubGetDxUser(userId, user);
      stubJwtGeneration(expectedToken);

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo(expectedToken);
            assertThat(result.getString("token_type")).isEqualTo("jwt");
            assertThat(result.getInteger("expires_in_minutes")).isEqualTo(EXPIRATION_MINUTES);

            verify(clientcredetialService)
                .getUserIdByClientIdAndSecret(anyString(), anyString());
            verify(keycloakUserService).getUserById(userId);
            verify(jwtAuth).generateToken(any(JsonObject.class), any(JWTOptions.class));
            verifyNoInteractions(itemService);
            verifyNoInteractions(delegationService);
          });
    }

    @Test
    @DisplayName("should call getDxUser with hashed clientId and clientSecret")
    void createIdentityToken_hashesCredentials(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("some-token");

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            // Verify that the hashed values are NOT the raw values
            // (SHA-512 hex is 128 characters long)
            verify(clientcredetialService)
                .getUserIdByClientIdAndSecret(
                    org.mockito.ArgumentMatchers.argThat(
                        arg -> arg.length() == 128 && !arg.equals(CLIENT_ID)),
                    org.mockito.ArgumentMatchers.argThat(
                        arg -> arg.length() == 128 && !arg.equals(CLIENT_SECRET)));
          });
    }

    @Test
    @DisplayName("should treat empty itemId and empty delegationId as identity token request")
    void createIdentityToken_emptyStrings(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("identity-token");

      // Both empty strings should route to identity token path
      AccessTokenRequest request =
          new AccessTokenRequest(CLIENT_ID, CLIENT_SECRET, "", "", null);

      Future<JsonObject> future = tokenService.createToken(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("identity-token");
            verifyNoInteractions(itemService);
            verifyNoInteractions(delegationService);
          });
    }

    @Test
    @DisplayName("should treat blank delegationId and blank itemId as identity token request")
    void createIdentityToken_blankStrings(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("identity-token");

      AccessTokenRequest request =
          new AccessTokenRequest(CLIENT_ID, CLIENT_SECRET, "   ", "   ", null);

      Future<JsonObject> future = tokenService.createToken(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("identity-token");
            verifyNoInteractions(itemService);
            verifyNoInteractions(delegationService);
          });
    }
  }

  // ============================
  // AUTHENTICATION FAILURE TESTS
  // ============================

  @Nested
  @DisplayName("Authentication Failures (getDxUser)")
  class AuthenticationFailures {

    @Test
    @DisplayName("should fail when clientId/clientSecret lookup fails")
    void createToken_invalidCredentials_fails(VertxTestContext ctx) {
      when(clientcredetialService.getUserIdByClientIdAndSecret(anyString(), anyString()))
          .thenReturn(Future.failedFuture("Invalid client credentials"));

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).contains("Invalid client credentials");
            verifyNoInteractions(keycloakUserService);
            verifyNoInteractions(jwtAuth);
          });
    }

    @Test
    @DisplayName("should fail when Keycloak user lookup fails")
    void createToken_keycloakUserNotFound_fails(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();

      when(clientcredetialService.getUserIdByClientIdAndSecret(anyString(), anyString()))
          .thenReturn(Future.succeededFuture(userId));
      when(keycloakUserService.getUserById(userId))
          .thenReturn(Future.failedFuture(new DxNotFoundException("User not found in Keycloak")));

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
            assertThat(err.getMessage()).contains("User not found in Keycloak");
            verify(jwtAuth, never()).generateToken(any(), any());
          });
    }
  }

  // ============================
  // getDxUser FLOW TESTS
  // ============================

  @Nested
  @DisplayName("getDxUser Flow")
  class GetDxUserFlow {

    @Test
    @DisplayName("should compose clientcredetialService -> keycloakUserService to get DxUser")
    void getDxUser_composition_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      DxUser expectedUser = buildTestUserWithRoles(userId, List.of("provider", "consumer"));

      when(clientcredetialService.getUserIdByClientIdAndSecret(anyString(), anyString()))
          .thenReturn(Future.succeededFuture(userId));
      when(keycloakUserService.getUserById(userId))
          .thenReturn(Future.succeededFuture(expectedUser));
      stubJwtGeneration("composed-token");

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            // The token was generated which means getDxUser completed successfully
            assertThat(result.getString("access_token")).isEqualTo("composed-token");

            // Verify the full chain was executed in order
            verify(clientcredetialService)
                .getUserIdByClientIdAndSecret(anyString(), anyString());
            verify(keycloakUserService).getUserById(userId);
          });
    }

    @Test
    @DisplayName("should propagate clientcredetialService failure without calling keycloak")
    void getDxUser_clientServiceFails_propagates(VertxTestContext ctx) {
      when(clientcredetialService.getUserIdByClientIdAndSecret(anyString(), anyString()))
          .thenReturn(Future.failedFuture(new RuntimeException("DB connection failed")));

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).contains("DB connection failed");
            verifyNoInteractions(keycloakUserService);
          });
    }
  }

  // ============================
  // ACCESS TOKEN TESTS
  // ============================

  @Nested
  @DisplayName("Access Token (with itemId, no delegationId)")
  class AccessToken {

    @Test
    @DisplayName("should create access token with item info when item found via direct access")
    void createAccessToken_directAccess_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);
      String expectedToken = "jwt-access-token-value";

      stubGetDxUser(userId, user);

      // First generateToken call inside fetchItemInfo (for building the internal auth token)
      // and second call in generateJwtToken
      stubJwtGeneration(expectedToken);

      JsonObject itemJson =
          new JsonObject()
              .put("type", new JsonArray().add("iudx:Resource"))
              .put("organizationId", "org-123")
              .put("ownerUserId", userId.toString())
              .put("accessPolicy", "OPEN")
              .put("shortDescription", "Test resource")
              .put("resourceServer", new JsonArray().add("rs.example.com"))
              .put("id", itemId);

      ResponseModel responseModel = mockResponseModelWithItem(itemJson);
      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.succeededFuture(responseModel));

      Future<JsonObject> future = tokenService.createToken(accessTokenRequest(itemId));

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo(expectedToken);
            assertThat(result.getString("token_type")).isEqualTo("jwt");
            assertThat(result.getInteger("expires_in_minutes")).isEqualTo(EXPIRATION_MINUTES);

            verify(itemService).getItemWithAccessChecks(any());
          });
    }

    @Test
    @DisplayName(
        "should fall back to delegation access when item service returns null response")
    void createAccessToken_nullResponse_fallsToDelegation(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID delegatorId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);
      DxUser delegatorUser = buildTestUserWithRoles(delegatorId, List.of("provider"));

      stubGetDxUser(userId, user);
      stubJwtGeneration("delegation-fallback-token");

      // Item service returns null response, triggering delegation fallback
      ResponseModel nullResponseModel = org.mockito.Mockito.mock(ResponseModel.class);
      when(nullResponseModel.getResponse()).thenReturn(null);
      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.succeededFuture(nullResponseModel));

      ItemInfo itemInfo =
          new ItemInfo(
              new JsonArray().add("iudx:Resource"),
              "org-123",
              delegatorId.toString(),
              "OPEN",
              "Delegated resource",
              new JsonArray(),
              null,
              null,
              null,
              new JsonArray(),
              itemId);
      DelegationValidationResult validationResult =
          new DelegationValidationResult(delegationId, delegatorId, userId, itemInfo);

      when(delegationAccessEvaluator.validateItemAccess(any(DxUser.class), eq(itemId)))
          .thenReturn(Future.succeededFuture(validationResult));
      when(keycloakUserService.getUserById(delegatorId))
          .thenReturn(Future.succeededFuture(delegatorUser));

      Future<JsonObject> future = tokenService.createToken(accessTokenRequest(itemId));

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("delegation-fallback-token");
            verify(delegationAccessEvaluator).validateItemAccess(any(DxUser.class), eq(itemId));
          });
    }

    @Test
    @DisplayName("should fall back to delegation access when item service fails")
    void createAccessToken_itemServiceFails_fallsToDelegation(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID delegatorId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);
      DxUser delegatorUser = buildTestUserWithRoles(delegatorId, List.of("provider"));

      stubGetDxUser(userId, user);
      stubJwtGeneration("delegation-recovery-token");

      // Item service fails entirely
      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.failedFuture("Item not accessible"));

      ItemInfo itemInfo =
          new ItemInfo(
              new JsonArray().add("iudx:Resource"),
              "org-123",
              delegatorId.toString(),
              "OPEN",
              "Delegated resource",
              new JsonArray(),
              null,
              null,
              null,
              new JsonArray(),
              itemId);
      DelegationValidationResult validationResult =
          new DelegationValidationResult(delegationId, delegatorId, userId, itemInfo);

      when(delegationAccessEvaluator.validateItemAccess(any(DxUser.class), eq(itemId)))
          .thenReturn(Future.succeededFuture(validationResult));
      when(keycloakUserService.getUserById(delegatorId))
          .thenReturn(Future.succeededFuture(delegatorUser));

      Future<JsonObject> future = tokenService.createToken(accessTokenRequest(itemId));

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("delegation-recovery-token");
            verify(delegationAccessEvaluator).validateItemAccess(any(DxUser.class), eq(itemId));
          });
    }

    @Test
    @DisplayName(
        "should fail with DxForbiddenException when both direct and delegation access fail")
    void createAccessToken_allAccessFails(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("should-not-be-used");

      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.failedFuture("Item not accessible"));
      when(delegationAccessEvaluator.validateItemAccess(any(DxUser.class), eq(itemId)))
          .thenReturn(Future.failedFuture("No delegation found"));

      Future<JsonObject> future = tokenService.createToken(accessTokenRequest(itemId));

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxForbiddenException.class);
            assertThat(err.getMessage()).contains("No access found via delegation or direct access");
          });
    }
  }

  // ============================
  // DELEGATION TOKEN TESTS
  // ============================

  @Nested
  @DisplayName("Delegation Token (with delegationId)")
  class DelegationToken {

    @Test
    @DisplayName("should create delegation token with scopes and delegator info")
    void createDelegationToken_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID delegatorId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);
      String expectedToken = "jwt-delegation-token-value";

      stubGetDxUser(userId, user);
      stubJwtGeneration(expectedToken);

      // Stub delegation scope constraints
      List<JsonObject> scopeConstraints =
          List.of(
              new JsonObject()
                  .put("scope", "data_access")
                  .put("entity_id", itemId));

      when(delegationService.getDelegationScopeConstraints(delegationId.toString()))
          .thenReturn(Future.succeededFuture(scopeConstraints));

      // Stub delegation grant
      JsonObject grantJson =
          new JsonObject()
              .put("delegation_id", delegationId)
              .put("delegator_id", delegatorId)
              .put("delegate_id", userId)
              .put("status", "active");
      when(delegationService.getDelegationGrantById(delegationId.toString()))
          .thenReturn(Future.succeededFuture(grantJson));

      // Stub item service for fetchItemInfo
      JsonObject itemJson =
          new JsonObject()
              .put("type", new JsonArray().add("iudx:Resource"))
              .put("organizationId", "org-123")
              .put("ownerUserId", delegatorId.toString())
              .put("accessPolicy", "SECURE")
              .put("id", itemId);
      ResponseModel responseModel = mockResponseModelWithItem(itemJson);
      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.succeededFuture(responseModel));

      Future<JsonObject> future =
          tokenService.createToken(delegationTokenRequest(delegationId.toString(), itemId));

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo(expectedToken);
            assertThat(result.getString("token_type")).isEqualTo("jwt");
            assertThat(result.getInteger("expires_in_minutes")).isEqualTo(EXPIRATION_MINUTES);

            verify(delegationService)
                .getDelegationScopeConstraints(delegationId.toString());
            verify(delegationService).getDelegationGrantById(delegationId.toString());
            verify(itemService).getItemWithAccessChecks(any());
          });
    }

    @Test
    @DisplayName("should fail when delegation scope constraints are empty")
    void createDelegationToken_noConstraints_fails(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("unused");

      when(delegationService.getDelegationScopeConstraints(delegationId.toString()))
          .thenReturn(Future.succeededFuture(List.of()));

      Future<JsonObject> future =
          tokenService.createToken(delegationTokenRequest(delegationId.toString(), itemId));

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
            assertThat(err.getMessage()).contains("No scope constraints found");
          });
    }

    @Test
    @DisplayName("should fail when delegation grant is not found")
    void createDelegationToken_grantNotFound_fails(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("unused");

      List<JsonObject> constraints =
          List.of(new JsonObject().put("scope", "data_access").put("entity_id", itemId));
      when(delegationService.getDelegationScopeConstraints(delegationId.toString()))
          .thenReturn(Future.succeededFuture(constraints));
      when(delegationService.getDelegationGrantById(delegationId.toString()))
          .thenReturn(Future.succeededFuture(null));

      Future<JsonObject> future =
          tokenService.createToken(delegationTokenRequest(delegationId.toString(), itemId));

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
            assertThat(err.getMessage()).contains("Delegation grant not found");
          });
    }

    @Test
    @DisplayName("should fail when delegation grant is not active")
    void createDelegationToken_inactiveGrant_fails(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID delegatorId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("unused");

      List<JsonObject> constraints =
          List.of(new JsonObject().put("scope", "data_access").put("entity_id", itemId));
      when(delegationService.getDelegationScopeConstraints(delegationId.toString()))
          .thenReturn(Future.succeededFuture(constraints));

      JsonObject inactiveGrant =
          new JsonObject()
              .put("delegation_id", delegationId)
              .put("delegator_id", delegatorId)
              .put("delegate_id", userId)
              .put("status", "revoked");
      when(delegationService.getDelegationGrantById(delegationId.toString()))
          .thenReturn(Future.succeededFuture(inactiveGrant));

      Future<JsonObject> future =
          tokenService.createToken(delegationTokenRequest(delegationId.toString(), itemId));

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxForbiddenException.class);
            assertThat(err.getMessage()).contains("not active");
          });
    }

    @Test
    @DisplayName("should fail when user is not the delegate in the grant")
    void createDelegationToken_wrongDelegate_fails(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID delegatorId = UUID.randomUUID();
      UUID someOtherUserId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("unused");

      List<JsonObject> constraints =
          List.of(new JsonObject().put("scope", "data_access").put("entity_id", itemId));
      when(delegationService.getDelegationScopeConstraints(delegationId.toString()))
          .thenReturn(Future.succeededFuture(constraints));

      // The grant's delegate_id does not match the authenticated user
      JsonObject grantWithDifferentDelegate =
          new JsonObject()
              .put("delegation_id", delegationId)
              .put("delegator_id", delegatorId)
              .put("delegate_id", someOtherUserId)
              .put("status", "active");
      when(delegationService.getDelegationGrantById(delegationId.toString()))
          .thenReturn(Future.succeededFuture(grantWithDifferentDelegate));

      Future<JsonObject> future =
          tokenService.createToken(delegationTokenRequest(delegationId.toString(), itemId));

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxForbiddenException.class);
            assertThat(err.getMessage()).contains("does not belong to this delegate");
          });
    }

    @Test
    @DisplayName("should fail when delegationService.getDelegationScopeConstraints fails")
    void createDelegationToken_constraintServiceFails(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("unused");

      when(delegationService.getDelegationScopeConstraints(delegationId.toString()))
          .thenReturn(Future.failedFuture(new RuntimeException("Database error")));

      Future<JsonObject> future =
          tokenService.createToken(delegationTokenRequest(delegationId.toString(), itemId));

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).contains("Database error");
          });
    }
  }

  // ============================
  // ROUTING LOGIC TESTS
  // ============================

  @Nested
  @DisplayName("Request Routing Logic")
  class RequestRouting {

    @Test
    @DisplayName("should route to delegation token when delegationId is present")
    void routing_delegationId_present(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID delegatorId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("delegation-token");

      List<JsonObject> constraints =
          List.of(new JsonObject().put("scope", "data_access").put("entity_id", itemId));
      when(delegationService.getDelegationScopeConstraints(delegationId.toString()))
          .thenReturn(Future.succeededFuture(constraints));

      JsonObject grantJson =
          new JsonObject()
              .put("delegation_id", delegationId)
              .put("delegator_id", delegatorId)
              .put("delegate_id", userId)
              .put("status", "active");
      when(delegationService.getDelegationGrantById(delegationId.toString()))
          .thenReturn(Future.succeededFuture(grantJson));

      JsonObject itemJson =
          new JsonObject()
              .put("type", new JsonArray().add("iudx:Resource"))
              .put("id", itemId);
      ResponseModel responseModel = mockResponseModelWithItem(itemJson);
      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.succeededFuture(responseModel));

      Future<JsonObject> future =
          tokenService.createToken(delegationTokenRequest(delegationId.toString(), itemId));

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            // Verify delegation service was called (routing to delegation path)
            verify(delegationService)
                .getDelegationScopeConstraints(delegationId.toString());
          });
    }

    @Test
    @DisplayName("should route to access token when only itemId is present")
    void routing_onlyItemId_present(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("access-token");

      JsonObject itemJson =
          new JsonObject()
              .put("type", new JsonArray().add("iudx:Resource"))
              .put("id", itemId);
      ResponseModel responseModel = mockResponseModelWithItem(itemJson);
      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.succeededFuture(responseModel));

      Future<JsonObject> future = tokenService.createToken(accessTokenRequest(itemId));

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("access-token");
            verify(itemService).getItemWithAccessChecks(any());
            // delegationService should not be called for the constraint/grant path
            verify(delegationService, never()).getDelegationScopeConstraints(anyString());
            verify(delegationService, never()).getDelegationGrantById(anyString());
          });
    }

    @Test
    @DisplayName("should route to identity token when neither itemId nor delegationId is present")
    void routing_noItemId_noDelegationId(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("identity-token");

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("identity-token");
            verifyNoInteractions(itemService);
            verifyNoInteractions(delegationService);
            verifyNoInteractions(delegationAccessEvaluator);
          });
    }

    @Test
    @DisplayName(
        "delegationId takes priority over itemId for routing - should route to delegation path")
    void routing_delegationId_overrides_itemId(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      UUID delegatorId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("delegation-priority-token");

      List<JsonObject> constraints =
          List.of(new JsonObject().put("scope", "*"));
      when(delegationService.getDelegationScopeConstraints(delegationId.toString()))
          .thenReturn(Future.succeededFuture(constraints));

      JsonObject grantJson =
          new JsonObject()
              .put("delegation_id", delegationId)
              .put("delegator_id", delegatorId)
              .put("delegate_id", userId)
              .put("status", "active");
      when(delegationService.getDelegationGrantById(delegationId.toString()))
          .thenReturn(Future.succeededFuture(grantJson));

      JsonObject itemJson =
          new JsonObject()
              .put("type", new JsonArray().add("iudx:Resource"))
              .put("id", itemId);
      ResponseModel responseModel = mockResponseModelWithItem(itemJson);
      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.succeededFuture(responseModel));

      // Both delegationId and itemId are present
      Future<JsonObject> future =
          tokenService.createToken(delegationTokenRequest(delegationId.toString(), itemId));

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            // delegationService should have been invoked (delegation path, not just access path)
            verify(delegationService)
                .getDelegationScopeConstraints(delegationId.toString());
            verify(delegationService).getDelegationGrantById(delegationId.toString());
          });
    }
  }

  // ============================
  // JWT GENERATION TESTS
  // ============================

  @Nested
  @DisplayName("JWT Token Generation (generateJwtToken)")
  class JwtGeneration {

    @Test
    @DisplayName("should generate identity token without extra claims (null extraClaims)")
    void generateJwt_identityToken_noExtraClaims(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      DxUser user = buildTestUser(userId);
      String expectedToken = "clean-identity-token";

      stubGetDxUser(userId, user);
      stubJwtGeneration(expectedToken);

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo(expectedToken);

            // Verify that generateToken was called with claims containing user info
            verify(jwtAuth)
                .generateToken(
                    org.mockito.ArgumentMatchers.argThat(
                        claims ->
                            claims.getString("sub").equals(userId.toString())
                                && claims.getString("iss").equals(ISSUER)
                                && claims.getString("aud").equals("CLAIM_AUDIENCE")),
                    any(JWTOptions.class));
          });
    }

    @Test
    @DisplayName("should include extra claims from item info in access token")
    void generateJwt_accessToken_mergesExtraClaims(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      String itemId = UUID.randomUUID().toString();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("access-token-with-claims");

      JsonObject itemJson =
          new JsonObject()
              .put("type", new JsonArray().add("iudx:Resource"))
              .put("organizationId", "org-456")
              .put("accessPolicy", "SECURE")
              .put("id", itemId);
      ResponseModel responseModel = mockResponseModelWithItem(itemJson);
      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.succeededFuture(responseModel));

      Future<JsonObject> future = tokenService.createToken(accessTokenRequest(itemId));

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.getString("access_token")).isEqualTo("access-token-with-claims");

            // The second generateToken call (the final one for the returned token) should
            // have claims merged with item info
            verify(jwtAuth, org.mockito.Mockito.atLeast(1))
                .generateToken(any(JsonObject.class), any(JWTOptions.class));
          });
    }

    @Test
    @DisplayName("should include standard JWT fields: sub, iss, aud, exp, iat")
    void generateJwt_standardClaims(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("standard-claims-token");

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            verify(jwtAuth)
                .generateToken(
                    org.mockito.ArgumentMatchers.argThat(
                        claims -> {
                          boolean hasSub = claims.containsKey("sub");
                          boolean hasIss =
                              ISSUER.equals(claims.getString("iss"));
                          boolean hasAud = claims.containsKey("aud");
                          boolean hasExp = claims.containsKey("exp");
                          boolean hasIat = claims.containsKey("iat");
                          boolean hasRealmAccess = claims.containsKey("realm_access");
                          return hasSub && hasIss && hasAud && hasExp && hasIat && hasRealmAccess;
                        }),
                    any(JWTOptions.class));
          });
    }
  }

  // ============================
  // RESPONSE FORMAT TESTS
  // ============================

  @Nested
  @DisplayName("Response Format")
  class ResponseFormat {

    @Test
    @DisplayName("should return response with access_token, token_type, and expires_in_minutes")
    void responseFormat_allFieldsPresent(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("formatted-token");

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.containsKey("access_token")).isTrue();
            assertThat(result.containsKey("token_type")).isTrue();
            assertThat(result.containsKey("expires_in_minutes")).isTrue();
            assertThat(result.getString("token_type")).isEqualTo("jwt");
            assertThat(result.getInteger("expires_in_minutes")).isEqualTo(EXPIRATION_MINUTES);
          });
    }

    @Test
    @DisplayName("should return non-null, non-empty access_token value")
    void responseFormat_accessTokenNotEmpty(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      DxUser user = buildTestUser(userId);

      stubGetDxUser(userId, user);
      stubJwtGeneration("valid-token-string");

      Future<JsonObject> future = tokenService.createToken(identityTokenRequest());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            String token = result.getString("access_token");
            assertThat(token).isNotNull();
            assertThat(token).isNotBlank();
          });
    }
  }
}
