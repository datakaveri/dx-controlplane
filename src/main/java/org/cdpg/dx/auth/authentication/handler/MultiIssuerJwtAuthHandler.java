package org.cdpg.dx.auth.authentication.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.TokenCredentials;
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

  private static String extractKid(String token) {
    String[] parts = token.split("\\.");
    if (parts.length < 2) throw new IllegalArgumentException("Malformed JWT");
    String header = new String(Base64.getUrlDecoder().decode(parts[0]));
    return new JsonObject(header).getString("kid");
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
    String kid;
    try {
      issuer = extractIssuer(token);
      kid = extractKid(token);
    } catch (Exception e) {
      LOGGER.error("Failed to extract token claims: {}", e.getMessage());
      ctx.fail(new DxUnauthorizedException("Invalid token format"));
      return;
    }

    jwksResolver
        .resolve(issuer, kid)
        .compose(jwtAuth -> jwtAuth.authenticate(new TokenCredentials(token)))
        .onSuccess(
            user -> {
              LOGGER.info("Authentication successful for issuer: {}, kid: {}", issuer, kid);
              ctx.setUser(user);
              ctx.next();
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Authentication failed for issuer {}, kid {}: {}", issuer, kid, err.getMessage());
              ctx.fail(new DxUnauthorizedException("Unauthorized: %s".formatted(err.getMessage())));
            });
  }
}
