package org.cdpg.dx.auth.authentication.lookup.local;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.vertx.core.Future;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.auth.model.AppPrincipal;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocalAppCredentialLookup Tests")
class LocalAppCredentialLookupTest {

  private static AppCredentials app(
      UUID appId, UUID userId, String hashedSecret, String status, String expiryAt, String revokedAt) {
    return new AppCredentials(
        appId, userId, hashedSecret, expiryAt, status, "consumer",
        LocalDateTime.now().toString(), LocalDateTime.now().toString(), revokedAt);
  }

  private static AppConstraints constraint(UUID appId, String scope) {
    return new AppConstraints(UUID.randomUUID(), appId, scope, "*", "*", UUID.randomUUID());
  }

  @Test
  @DisplayName("valid secret + active app → AppPrincipal with scope list")
  void happyPath() {
    AppCredentialsService svc = mock(AppCredentialsService.class);
    UUID appId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String hash = DigestUtils.sha512Hex("my-secret");

    when(svc.getAppById(appId))
        .thenReturn(Future.succeededFuture(app(appId, userId, hash, "active", null, null)));
    when(svc.getAppConstraintsById(appId))
        .thenReturn(
            Future.succeededFuture(
                List.of(constraint(appId, "own-asset-management"), constraint(appId, "data-access"))));

    Optional<AppPrincipal> result =
        new LocalAppCredentialLookup(svc).verify(appId.toString(), "my-secret").result();

    assertTrue(result.isPresent());
    AppPrincipal p = result.get();
    assertEquals(appId.toString(), p.appId());
    assertEquals(userId.toString(), p.ownerSub());
    assertNull(p.ownerOrgId(), "lookup doesn't know orgId — resolver fills from UserLookup");
    assertTrue(p.active());
    assertEquals(2, p.appScopes().size());
    assertTrue(p.appScopes().contains("own-asset-management"));
    assertTrue(p.appScopes().contains("data-access"));
  }

  @Test
  @DisplayName("wrong secret → Optional.empty")
  void wrongSecret() {
    AppCredentialsService svc = mock(AppCredentialsService.class);
    UUID appId = UUID.randomUUID();
    String hash = DigestUtils.sha512Hex("real-secret");

    when(svc.getAppById(appId))
        .thenReturn(
            Future.succeededFuture(app(appId, UUID.randomUUID(), hash, "active", null, null)));

    Optional<AppPrincipal> result =
        new LocalAppCredentialLookup(svc).verify(appId.toString(), "wrong").result();
    assertTrue(result.isEmpty());
    verify(svc, never()).getAppConstraintsById(any());
  }

  @Test
  @DisplayName("revoked app → Optional.empty")
  void revoked() {
    AppCredentialsService svc = mock(AppCredentialsService.class);
    UUID appId = UUID.randomUUID();
    String hash = DigestUtils.sha512Hex("x");

    when(svc.getAppById(appId))
        .thenReturn(
            Future.succeededFuture(
                app(appId, UUID.randomUUID(), hash, "active", null, LocalDateTime.now().toString())));

    Optional<AppPrincipal> result =
        new LocalAppCredentialLookup(svc).verify(appId.toString(), "x").result();
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("inactive status → Optional.empty")
  void inactiveStatus() {
    AppCredentialsService svc = mock(AppCredentialsService.class);
    UUID appId = UUID.randomUUID();
    String hash = DigestUtils.sha512Hex("x");

    when(svc.getAppById(appId))
        .thenReturn(Future.succeededFuture(app(appId, UUID.randomUUID(), hash, "revoked", null, null)));

    Optional<AppPrincipal> result =
        new LocalAppCredentialLookup(svc).verify(appId.toString(), "x").result();
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("expired app → Optional.empty")
  void expired() {
    AppCredentialsService svc = mock(AppCredentialsService.class);
    UUID appId = UUID.randomUUID();
    String hash = DigestUtils.sha512Hex("x");
    String pastExpiry = LocalDateTime.now().minusDays(1).toString();

    when(svc.getAppById(appId))
        .thenReturn(
            Future.succeededFuture(
                app(appId, UUID.randomUUID(), hash, "active", pastExpiry, null)));

    Optional<AppPrincipal> result =
        new LocalAppCredentialLookup(svc).verify(appId.toString(), "x").result();
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("DxNotFoundException → Optional.empty")
  void notFound() {
    AppCredentialsService svc = mock(AppCredentialsService.class);
    UUID appId = UUID.randomUUID();
    when(svc.getAppById(appId))
        .thenReturn(Future.failedFuture(new DxNotFoundException("no such app")));

    Optional<AppPrincipal> result =
        new LocalAppCredentialLookup(svc).verify(appId.toString(), "x").result();
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("non-UUID appId → Optional.empty without service call")
  void invalidAppId() {
    AppCredentialsService svc = mock(AppCredentialsService.class);
    Optional<AppPrincipal> result =
        new LocalAppCredentialLookup(svc).verify("not-a-uuid", "x").result();
    assertTrue(result.isEmpty());
    verifyNoInteractions(svc);
  }

  @Test
  @DisplayName("blank secret or appId → Optional.empty without service call")
  void blankCreds() {
    AppCredentialsService svc = mock(AppCredentialsService.class);
    assertTrue(new LocalAppCredentialLookup(svc).verify("", "x").result().isEmpty());
    assertTrue(
        new LocalAppCredentialLookup(svc)
            .verify(UUID.randomUUID().toString(), "")
            .result()
            .isEmpty());
    verifyNoInteractions(svc);
  }

  @Test
  @DisplayName("service transport error → failed Future")
  void transportError() {
    AppCredentialsService svc = mock(AppCredentialsService.class);
    UUID appId = UUID.randomUUID();
    RuntimeException boom = new RuntimeException("db down");
    when(svc.getAppById(appId)).thenReturn(Future.failedFuture(boom));

    Future<Optional<AppPrincipal>> f =
        new LocalAppCredentialLookup(svc).verify(appId.toString(), "x");
    assertTrue(f.failed());
    assertSame(boom, f.cause());
  }
}