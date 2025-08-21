package org.cdpg.dx.auth.authentication.provider;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.publicKey.service.PublicService;
import org.cdpg.dx.aaa.publicKey.service.impl.PublicServiceImpl;
import org.cdpg.dx.auth.authentication.client.JwksClient;
import org.cdpg.dx.auth.authentication.util.TokenIssuer;

/** Centralized JWTAuth provider that supports AAA (internal JWKS) and Keycloak (HTTP JWKS). */
public class JwtAuthProvider {

  private static final Logger LOGGER = LogManager.getLogger(JwtAuthProvider.class);
  private static JWTAuth jwtAuth;
  private static long refreshTimerId;

  public static Future<JWTAuth> init(Vertx vertx, JsonObject config, TokenIssuer tokenIssuer) {
    String certUrl =
        tokenIssuer.equals(TokenIssuer.AAA)
            ? config.getString("aaaCertUrl")
            : config.getString("keycloakCertUrl");

    long refreshMs = config.getLong("jwksRefreshIntervalMs", 6 * 60 * 60 * 1000L); // default: 6h

    String keyStorePath = config.getString("keystorePath");
    String keyStorePassword = config.getString("keystorePassword");

    PublicService aaaKeyProvider = new PublicServiceImpl(keyStorePath, keyStorePassword, vertx);
    JwksClient jwksClient = new JwksClient(vertx, certUrl, tokenIssuer, aaaKeyProvider);

    return refresh(vertx, config, jwksClient, tokenIssuer)
        .onSuccess(
            jwt -> {
              if (refreshTimerId == 0) {
                refreshTimerId =
                    vertx.setPeriodic(
                        refreshMs, id -> refresh(vertx, config, jwksClient, tokenIssuer));
                LOGGER.info("JWKs auto-refresh enabled every {} ms", refreshMs);
              }
            });
  }

  private static Future<JWTAuth> refresh(
      Vertx vertx, JsonObject config, JwksClient jwksClient, TokenIssuer tokenIssuer) {
    return jwksClient
        .fetchJwkKeys()
        .compose(
            jwk -> {
              List<JsonObject> keys =
                  jwk.getJsonArray("keys").stream()
                      .map(obj -> (JsonObject) obj)
                      .collect(Collectors.toList());

              String iss =
                  tokenIssuer.equals(TokenIssuer.AAA)
                      ? config.getString("aaaIss")
                      : config.getString("kcIss");

              LOGGER.debug("Using issuer: {}, {}", tokenIssuer, iss);

              JWTAuthOptions options =
                  new JWTAuthOptions()
                      .setJwks(keys)
                      .setJWTOptions(
                          new JWTOptions()
                              .setLeeway(30)
                              .setIgnoreExpiration(config.getBoolean("jwtIgnoreExpiry", false))
                              .setIssuer(iss));

              // TODO: Add audience if needed
              // .setAudience(List.of(config.getString("aud")))

              jwtAuth = JWTAuth.create(vertx, options);
              LOGGER.info("JWTAuth initialized/refreshed successfully.");
              return Future.succeededFuture(jwtAuth);
            });
  }

  public static JWTAuth get() {
    if (jwtAuth == null)
      throw new IllegalStateException("JWTAuth not initialized. Call init() first.");
    return jwtAuth;
  }
}
