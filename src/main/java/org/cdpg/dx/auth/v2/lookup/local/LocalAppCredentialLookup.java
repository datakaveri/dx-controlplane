package org.cdpg.dx.auth.v2.lookup.local;

import io.vertx.core.Future;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.auth.v2.lookup.AppCredentialLookup;
import org.cdpg.dx.auth.v2.model.AppPrincipal;
import org.cdpg.dx.common.exception.DxNotFoundException;

/**
 * In-process {@link AppCredentialLookup} for dx-controlplane. Wraps {@link AppCredentialsService}
 * and performs the same credential-validation logic as the gRPC {@code AppIdVerificationGrpcService}
 * (SHA-512 hash match, status, expiry) so behaviour is identical whether the caller is local or
 * remote.
 *
 * <p>Returns {@code Optional.empty()} for any credential rejection (invalid secret, revoked,
 * expired, not found) — the resolver translates that into 401. The {@code Future} is failed only
 * for transport / infrastructure errors.
 */
public final class LocalAppCredentialLookup implements AppCredentialLookup {

  private static final ZoneId EXPIRY_ZONE = ZoneId.of("Asia/Kolkata");

  private final AppCredentialsService appCredentialsService;

  public LocalAppCredentialLookup(AppCredentialsService appCredentialsService) {
    this.appCredentialsService = Objects.requireNonNull(appCredentialsService, "appCredentialsService");
  }

  @Override
  public Future<Optional<AppPrincipal>> verify(String appId, String appSecret) {
    if (appId == null || appSecret == null || appId.isBlank() || appSecret.isBlank()) {
      return Future.succeededFuture(Optional.empty());
    }
    UUID appUuid;
    try {
      appUuid = UUID.fromString(appId);
    } catch (IllegalArgumentException e) {
      return Future.succeededFuture(Optional.empty());
    }

    return appCredentialsService
        .getAppById(appUuid)
        .compose(
            app -> {
              if (!validates(app, appSecret)) {
                return Future.succeededFuture(Optional.<AppPrincipal>empty());
              }
              return appCredentialsService
                  .getAppConstraintsById(appUuid)
                  .map(constraints -> Optional.of(buildPrincipal(app, constraints)));
            })
        .recover(err -> {
          if (err instanceof DxNotFoundException) {
            return Future.succeededFuture(Optional.empty());
          }
          return Future.failedFuture(err);
        });
  }

  private static boolean validates(AppCredentials app, String inputSecret) {
    if (app == null) return false;
    if (app.revokedAt() != null) return false;
    if (!"active".equalsIgnoreCase(app.status())) return false;
    if (app.expiryAt() != null) {
      Instant expiry = LocalDateTime.parse(app.expiryAt()).atZone(EXPIRY_ZONE).toInstant();
      if (expiry.isBefore(Instant.now())) return false;
    }
    return DigestUtils.sha512Hex(inputSecret).equals(app.appSecret());
  }

  private static AppPrincipal buildPrincipal(
      AppCredentials app, List<AppConstraints> constraints) {
    System.out.println("constraints: " + constraints.getFirst().toJson());
    List<String> scopes =
        constraints.stream()
            .map(AppConstraints::scope)
            .filter(s -> s != null && !s.isBlank())
            .distinct()
            .toList();
    long expiresAtEpoch = 0L;
    if (app.expiryAt() != null) {
      expiresAtEpoch =
          LocalDateTime.parse(app.expiryAt()).atZone(EXPIRY_ZONE).toInstant().getEpochSecond();
    }
    return new AppPrincipal(
        app.appId().toString(),
        app.userId() != null ? app.userId().toString() : null,
        null,
        scopes,
        expiresAtEpoch,
        true);
  }
}