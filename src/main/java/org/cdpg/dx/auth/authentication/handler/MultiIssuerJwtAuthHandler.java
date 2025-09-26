package org.cdpg.dx.auth.authentication.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import java.util.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.client.JwksResolver;
import org.cdpg.dx.auth.authentication.util.BearerTokenExtractor;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

public class MultiIssuerJwtAuthHandler implements AuthenticationHandler {
  private static final Logger LOGGER = LogManager.getLogger(MultiIssuerJwtAuthHandler.class);

  private final JwksResolver jwksResolver;

  public MultiIssuerJwtAuthHandler(JwksResolver resolver) {
    this.jwksResolver = resolver;
  }

  private static String extractIssuer(String token) {
    String[] parts = token.split("\\.");
    if (parts.length < 2) throw new IllegalArgumentException("Malformed JWT");
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
    return new JsonObject(payload).getString("iss");
  }

  @Override
  public void handle(RoutingContext ctx) {
    String token = BearerTokenExtractor.extract(ctx);
    if (token == null || token.isBlank()) {
      LOGGER.warn("Missing or invalid Authorization header");
      ctx.fail(new DxUnauthorizedException("Missing Bearer token"));
      return;
    }

    String issuer;
    try {
      issuer = extractIssuer(token);
    } catch (Exception e) {
      LOGGER.error("Failed to extract issuer: {}", e.getMessage());
      ctx.fail(new DxUnauthorizedException("Invalid token format"));
      return;
    }

    getOrCreateAuth(issuer)
        .compose(jwtAuth -> jwtAuth.authenticate(new JsonObject().put("token", token)))
        .onSuccess(
            user -> {
              LOGGER.info("Authentication successful for issuer: {}", issuer);
              ctx.setUser(user);
              ctx.next();
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Authentication failed for issuer {}: {}", issuer, err.getMessage(), err);
              ctx.fail(new DxUnauthorizedException("Unauthorized: %s".formatted(err.getMessage())));
            });
  }

  private Future<JWTAuth> getOrCreateAuth(String issuer) {
    LOGGER.debug("Resolving JWTAuth for issuer: {}", issuer);
    return jwksResolver.resolve(issuer);
  }
}
