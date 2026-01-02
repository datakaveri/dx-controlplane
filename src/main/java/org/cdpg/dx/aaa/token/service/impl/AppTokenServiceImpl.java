package org.cdpg.dx.aaa.token.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.token.model.AppTokenRequest;
import org.cdpg.dx.aaa.token.service.AppTokenService;
import org.cdpg.dx.aaa.token.util.TokenClaimsBuilder;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.time.*;
import java.util.UUID;

public class AppTokenServiceImpl implements AppTokenService {

  private static final Logger LOGGER = LogManager.getLogger(AppTokenServiceImpl.class);
  private static final String JWT_ALGORITHM = "ES256";

  private final JWTAuth jwtAuth;
  private final AppCredentialsService appCredentialsService;
  private final KeycloakUserService keycloakUserService;
  private final JWTOptions jwtOptions;
  private final String issuer;
  private final int tokenExpirationMinutes;

  public AppTokenServiceImpl(
      JWTAuth jwtAuth,
      KeycloakUserService keycloakUserService,
      AppCredentialsService appCredentialsService,
      String issuer,
      int tokenExpirationMinutes,
      Vertx vertx) {

    this.jwtAuth = jwtAuth;
    this.keycloakUserService = keycloakUserService;
    this.appCredentialsService = appCredentialsService;
    this.issuer = issuer;
    this.tokenExpirationMinutes = tokenExpirationMinutes;
    this.jwtOptions = new JWTOptions().setAlgorithm(JWT_ALGORITHM).setIssuer(issuer);
  }

  @Override
  public Future<JsonObject> createToken(AppTokenRequest request) {

    if (request == null || request.appId() == null || request.appSecret() == null) {
      return Future.failedFuture("invalid_app_credentials");
    }

    UUID appId = request.appId();
    String inputSecret = request.appSecret().trim();

    return appCredentialsService
        .getAppById(appId)
        .compose(app -> validateApp(app, inputSecret))
        .compose(this::issueAppToken);
  }

  /* -------------------------------------------------
   * Validation
   * ------------------------------------------------- */

  private Future<AppCredentials> validateApp(AppCredentials app, String inputSecret) {

    if (app == null) {
      return Future.failedFuture(new DxUnauthorizedException("invalid app credentials"));
    }

    if (app.revokedAt() != null) {
      return Future.failedFuture(new DxForbiddenException("App has been revoked"));
    }

    if (!"active".equalsIgnoreCase(app.status())) {
      return Future.failedFuture(new DxForbiddenException("App is not active"));
    }

    Instant expiryInstant =
        LocalDateTime.parse(app.expiryAt()).atZone(ZoneId.of("Asia/Kolkata")).toInstant();

    if (expiryInstant.isBefore(Instant.now())) {
      return Future.failedFuture(new DxUnauthorizedException("App has been expired"));
    }

    String hashedInputSecret = hash(inputSecret);
    if (!hashedInputSecret.equals(app.appSecret())) {
      return Future.failedFuture(new DxUnauthorizedException("invalid credentials"));
    }

    return Future.succeededFuture(app);
  }

  /* -------------------------------------------------
   * Token issuance
   * ------------------------------------------------- */

  private Future<JsonObject> issueAppToken(AppCredentials app) {

    return keycloakUserService
        .getUserById(app.userId())
        .compose(
            user -> {
              JsonObject extraClaims = new JsonObject().put("appId", app.appId().toString());

              return generateJwtToken(user, extraClaims);
            });
  }

  private Future<JsonObject> generateJwtToken(DxUser user, JsonObject extraClaims) {

    JsonObject claims =
        TokenClaimsBuilder.buildClaims(user, issuer, "CLAIM_AUDIENCE", tokenExpirationMinutes);

    claims.mergeIn(extraClaims);
    JsonObject realmAccess = new JsonObject();
    JsonArray rolesArray = new JsonArray().add("consumer");
    realmAccess.put("roles", rolesArray);
    claims.put("realm_access", realmAccess);

    String token = jwtAuth.generateToken(claims, jwtOptions);

    return Future.succeededFuture(
        new JsonObject()
            .put("access_token", token)
            .put("token_type", "Bearer")
            .put("expires_in_minutes", tokenExpirationMinutes));
  }

  /* -------------------------------------------------
   * Utilities
   * ------------------------------------------------- */

  private String hash(String input) {
    return DigestUtils.sha512Hex(input);
  }
}
