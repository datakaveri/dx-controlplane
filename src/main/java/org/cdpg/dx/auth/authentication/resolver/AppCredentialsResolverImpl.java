package org.cdpg.dx.auth.authentication.resolver;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.auth.authentication.resolver.AppCredentialsResolver;
import org.cdpg.dx.auth.authorization.registry.SystemRoleScopeMap;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.DateTimeHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves app credentials by validating the app state, then computing capped scopes as the
 * intersection of the app's configured scopes and the owner's role-derived scopes.
 */
public final class AppCredentialsResolverImpl implements AppCredentialsResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(AppCredentialsResolverImpl.class);
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  private final AppCredentialsService appCredentialsService;
  private final KeycloakUserService keycloakUserService;

  public AppCredentialsResolverImpl(
      AppCredentialsService appCredentialsService, KeycloakUserService keycloakUserService) {
    this.appCredentialsService = appCredentialsService;
    this.keycloakUserService = keycloakUserService;
  }

  @Override
  public Future<DxUser> resolve(String appId, String secret) {
    // 1. Parse appId as UUID
    UUID appUuid;
    try {
      appUuid = UUID.fromString(appId);
    } catch (IllegalArgumentException e) {
      return Future.failedFuture(new DxUnauthorizedException("Invalid app credentials"));
    }

    return appCredentialsService
        .getAppById(appUuid)
        .compose(app -> validateApp(app, secret))
        .compose(
            app ->
                appCredentialsService
                    .getAppConstraintsById(appUuid)
                    .compose(
                        constraints ->
                            keycloakUserService
                                .getUserById(app.userId())
                                .compose(owner -> buildDxUser(owner, constraints, appId))))
        .recover(
            err -> {
              if (err instanceof DxUnauthorizedException || err instanceof DxForbiddenException) {
                return Future.failedFuture(err);
              }
              LOGGER.warn("App credential resolution failed: {}", err.getMessage());
              return Future.failedFuture(new DxUnauthorizedException("Invalid app credentials"));
            });
  }

  /** Validates the app record: revoked, status, expiry, secret hash. Returns the app if valid. */
  private Future<AppCredentials> validateApp(AppCredentials app, String secret) {
    // Revoked check
    if (app.revokedAt() != null) {
      return Future.failedFuture(new DxUnauthorizedException("Invalid app credentials"));
    }

    // Status check
    if (!"active".equalsIgnoreCase(app.status())) {
      return Future.failedFuture(new DxUnauthorizedException("Invalid app credentials"));
    }

    // Expiry check
    String expiryAtStr = app.expiryAt();
    if (expiryAtStr != null && !expiryAtStr.isBlank()) {
      LocalDateTime expiryLdt = DateTimeHelper.parseDateTime(expiryAtStr);
      if (expiryLdt != null) {
        ZonedDateTime expiryZdt = expiryLdt.atZone(IST);
        if (expiryZdt.isBefore(ZonedDateTime.now(IST))) {
          return Future.failedFuture(new DxUnauthorizedException("Invalid app credentials"));
        }
      }
    }

    // Hash check
    if (!DigestUtils.sha512Hex(secret).equals(app.appSecret())) {
      return Future.failedFuture(new DxUnauthorizedException("Invalid app credentials"));
    }

    return Future.succeededFuture(app);
  }

  /**
   * Builds the returned DxUser with capped scopes = appScopes ∩ ownerRoleScopes, preserving all
   * owner fields.
   */
  private Future<DxUser> buildDxUser(DxUser owner, List<AppConstraints> constraints, String appId) {
    // Check owner is still active
    if (!Boolean.TRUE.equals(owner.account_enabled())) {
      return Future.failedFuture(new DxForbiddenException("App owner is no longer active"));
    }

    // Collect app-configured scopes (distinct, non-blank)
    Set<String> appScopes =
        constraints.stream()
            .map(AppConstraints::scope)
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.toCollection(HashSet::new));

    // Flatten all scopes the owner's roles permit
    Set<String> ownerScopes = new HashSet<>();
    if (owner.roles() != null) {
      for (String roleStr : owner.roles()) {
        DxRole.fromString(roleStr)
            .ifPresent(role -> ownerScopes.addAll(SystemRoleScopeMap.getScopes(role)));
      }
    }

    // Wildcard app → grant all of owner's role scopes
    // Explicit app → capped = appScopes ∩ ownerScopes
    Set<String> cappedScopes =
        appScopes.contains("*")
            ? ownerScopes
            : appScopes.stream().filter(ownerScopes::contains).collect(Collectors.toSet());

    JsonArray scopesArray = new JsonArray(List.copyOf(cappedScopes));

    DxUser result =
        new DxUser(
            owner.roles(),
            owner.organisationId(),
            owner.organisationName(),
            owner.sub(),
            owner.emailVerified(),
            owner.kycVerified(),
            owner.name(),
            owner.preferredUsername(),
            owner.givenName(),
            owner.familyName(),
            owner.email(),
            owner.pendingRoles(),
            owner.organisation(),
            owner.createdAt(),
            owner.kycData(),
            owner.twitter_account(),
            owner.linkedin_account(),
            owner.github_account(),
            owner.account_enabled(),
            owner.did(),
            owner.aud(),
            scopesArray,
            null, // delegateeId
            appId); // appId — set for audit

    return Future.succeededFuture(result);
  }
}
