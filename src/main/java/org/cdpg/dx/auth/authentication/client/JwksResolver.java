package org.cdpg.dx.auth.authentication.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.publicKey.service.PublicService;

public class JwksResolver {
  private static final Logger LOGGER = LogManager.getLogger(JwksResolver.class);

  private final Map<String, JWTAuth> cache = new ConcurrentHashMap<>();
  private final JsonObject issuerConfig;
  private final JwksClient jwksClient;
  private final boolean ignoreExpiry;
  private final int leeway;
  private final Vertx vertx;

  public JwksResolver(Vertx vertx, JsonObject issuerConfig, PublicService aaaKeyProvider) {
    this.issuerConfig = issuerConfig;
    this.vertx = vertx;
    this.ignoreExpiry = issuerConfig.getBoolean("jwtIgnoreExpiry", false);
    this.leeway = issuerConfig.getInteger("jwtLeeway", 60);
    this.jwksClient = new JwksClient(vertx, aaaKeyProvider);

    // reset cache every N milliseconds
    long resetIntervalMs =
        issuerConfig.getLong("jwksRefreshIntervalMs", 600_000L); // default 10 minutes = 600,000 ms

    vertx.setPeriodic(
        resetIntervalMs,
        id -> {
          LOGGER.info("Resetting JWKS cache after {} ms", resetIntervalMs);
          cache.clear();
        });
  }

  public Future<JWTAuth> resolve(String issuer) {
    LOGGER.debug("Resolving JWTAuth for issuer: {}", issuer);

    if (cache.containsKey(issuer)) {
      LOGGER.info("cache hit for issuer {}", issuer);
      return Future.succeededFuture(cache.get(issuer));
    }
    LOGGER.info("cache miss - need to create JWTAuth provider for issuer {}", issuer);

    JsonObject cfg = issuerConfig.getJsonObject(issuer);
    if (cfg == null) {
      return Future.failedFuture("Unknown issuer: " + issuer);
    }
    String type = cfg.getString("type", "remote");
    String jwksUrl = cfg.getString("jwksUrl");

    return jwksClient
        .fetchJwks(type, jwksUrl)
        .map(
            jwks -> {
              List<JsonObject> keys =
                  jwks.getJsonArray("keys").stream()
                      .map(obj -> (JsonObject) obj)
                      .collect(Collectors.toList());

              JWTAuthOptions options =
                  new JWTAuthOptions()
                      .setJwks(keys)
                      .setJWTOptions(
                          new JWTOptions()
                              .setLeeway(leeway)
                              .setIgnoreExpiration(ignoreExpiry)
                              .setIssuer(issuer));

              JWTAuth jwtAuth = JWTAuth.create(vertx, options);
              cache.put(issuer, jwtAuth);

              LOGGER.info("Created new JWTAuth provider for issuer {}", issuer);
              return jwtAuth;
            });
  }
}
