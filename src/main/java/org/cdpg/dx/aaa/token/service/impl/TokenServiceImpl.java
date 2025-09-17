package org.cdpg.dx.aaa.token.service.impl;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import java.security.KeyStore;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialService;
import org.cdpg.dx.aaa.token.service.TokenService;
import org.cdpg.dx.aaa.token.util.TokenClaimsBuilder;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class TokenServiceImpl implements TokenService {
  public static final String JWT_ALGORITHM = "ES256";
  private final Logger LOGGER = LogManager.getLogger(TokenServiceImpl.class);
  private final JWTAuth provider;
  private final KeycloakUserService keycloakUserService;
  private final ClientcredetialService clientcredetialService;
  private final String keystorePath;
  private final String keystorePassword;
  private final int TokenExpirationMinutes;
  private final Vertx vertx;
  private final String issuer;

  public TokenServiceImpl(
      JWTAuth provider,
      KeycloakUserService keycloakUserService,
      ClientcredetialService clientcredetialService,
      String keystorePath,
      String keystorePassword,
      int expirationMinutes,
      String issuer,
      Vertx vertx) {
    this.provider = provider;
    this.keycloakUserService = keycloakUserService;
    this.clientcredetialService = clientcredetialService;
    this.keystorePath = keystorePath;
    this.keystorePassword = keystorePassword;
    this.TokenExpirationMinutes = expirationMinutes;
    this.issuer = issuer;
    this.vertx = vertx;
  }

  @Override
  public Future<JsonObject> createToken(String clientId, String clientSecret) {
    String hashedClientId = getHashedString(clientId.trim());
    String hashedClientSecret = getHashedString(clientSecret.trim());

    // JWT Options
    JWTOptions options = new JWTOptions().setAlgorithm(JWT_ALGORITHM).setIssuer(issuer);

    return clientcredetialService
        .getUserIdByClientIdAndSecret(hashedClientId, hashedClientSecret)
        .compose(keycloakUserService::getUserById)
        .compose(
            user -> {
              // Debug logs
              LOGGER.debug("Generating token for user: {}", user.sub());
              LOGGER.debug("Issuer: {}", issuer);
              LOGGER.debug("Token expiration (minutes): {}", TokenExpirationMinutes);
              LOGGER.debug("Audience: CLAIM_AUDIENCE");

              // TODO: dynamically determine audience (from config or user attributes)
              JsonObject claims =
                  TokenClaimsBuilder.buildClaims(
                      user, issuer, "CLAIM_AUDIENCE", TokenExpirationMinutes);

              // Generate signed token
              String token = provider.generateToken(claims, options);

              // Response
              return Future.succeededFuture(
                  new JsonObject()
                      .put("access_token", token)
                      .put("token_type", "jwt")
                      .put("expires_in_minutes", TokenExpirationMinutes)); // ✅ extra convenience
            });
  }

  @Override
  public JsonObject generateJwks() {
    try {
      JksOptions options = new JksOptions().setPath(keystorePath).setPassword(keystorePassword);
      KeyStore ks = options.loadKeyStore(vertx);
      ECKey ecKey = ECKey.load(ks, JWT_ALGORITHM, keystorePassword.toCharArray());

      JWKSet jwkSet = new JWKSet(ecKey.toPublicJWK());

      return new JsonObject(jwkSet.toJSONObject(true));

    } catch (Exception e) {
      LOGGER.error("Error retrieving public key from JKS: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to retrieve public key from JKS", e);
    }
  }

  private String getHashedString(String input) {
    return DigestUtils.sha512Hex(input);
  }
}
