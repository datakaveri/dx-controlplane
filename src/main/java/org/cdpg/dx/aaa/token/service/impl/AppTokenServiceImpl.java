package org.cdpg.dx.aaa.token.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.token.model.AppTokenRequest;
import org.cdpg.dx.aaa.token.service.AppTokenService;
import org.cdpg.dx.aaa.token.util.TokenClaimsBuilder;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.time.Instant;
import java.util.UUID;

public class AppTokenServiceImpl implements AppTokenService {

  private static final String JWT_ALGORITHM = "ES256";
  private final Logger LOGGER = LogManager.getLogger(AppTokenServiceImpl.class);

  private final JWTAuth provider;
  private final KeycloakUserService keycloakUserService;
  private final ClientcredetialService clientcredetialService;
  private final ItemService itemService;
  private final String issuer;
  private final JWTOptions options;
  private final int tokenExpirationMinutes;
  private final Vertx vertx;

  public AppTokenServiceImpl(
      JWTAuth provider,
      KeycloakUserService keycloakUserService,
      ClientcredetialService clientcredetialService,
      ItemService itemService,
      String issuer,
      int expirationMinutes,
      Vertx vertx) {
    this.provider = provider;
    this.keycloakUserService = keycloakUserService;
    this.clientcredetialService = clientcredetialService;
    this.itemService = itemService;
    this.issuer = issuer;
    this.tokenExpirationMinutes = expirationMinutes;
    this.vertx = vertx;
    this.options = new JWTOptions().setAlgorithm(JWT_ALGORITHM).setIssuer(issuer);
  }

  @Override
  public Future<JsonObject> createToken(AppTokenRequest request) {
    if (request.appId() == null || request.appSecret() == null) {
      return Future.failedFuture("Missing appId or appSecret");
    }

    return null;
  }

  private Future<DxUser> getDxUser(String clientId, String clientSecret) {
    String hashedClientId = hash(clientId);
    String hashedClientSecret = hash(clientSecret);
    return clientcredetialService
        .getUserIdByClientIdAndSecret(hashedClientId, hashedClientSecret)
        .compose(keycloakUserService::getUserById);
  }

  private Future<JsonObject> generateJwtToken(DxUser user, JsonObject extraClaims) {
    LOGGER.debug("Inside generation of tokens : {}", extraClaims);
    JsonObject claims =
            TokenClaimsBuilder.buildClaims(user, issuer, "CLAIM_AUDIENCE", tokenExpirationMinutes);
    if (extraClaims != null ) {
      claims.mergeIn(extraClaims);
    }

    LOGGER.info("claims2 :{}",claims);
    String token = provider.generateToken(claims, options);
    return Future.succeededFuture(
            new JsonObject()
                    .put("access_token", token)
                    .put("token_type", "jwt")
                    .put("expires_in_minutes", tokenExpirationMinutes));
  }

  /*public Future<AppRecord> validateApp(UUID appId, String inputSecret) {
    return appDao.fetchByAppId(appId)
            .compose(app -> {
              if (app == null) {
                return Future.failedFuture(AuthError.INVALID_CREDENTIALS);
              }

              if (!"active".equals(app.status())) {
                return Future.failedFuture(AuthError.APP_NOT_ACTIVE);
              }

              if (app.expiryAt().isBefore(Instant.now())) {
                return Future.failedFuture(AuthError.APP_EXPIRED);
              }

              if (!passwordEncoder.matches(inputSecret, app.secretHash())) {
                return Future.failedFuture(AuthError.INVALID_CREDENTIALS);
              }

              return Future.succeededFuture(app);
            });
  }*/


  private String hash(String input) {
    return DigestUtils.sha512Hex(input.trim());
  }
}
