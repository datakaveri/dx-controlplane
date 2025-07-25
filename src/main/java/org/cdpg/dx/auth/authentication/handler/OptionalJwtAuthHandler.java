package org.cdpg.dx.auth.authentication.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.util.BearerTokenExtractor;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

public class OptionalJwtAuthHandler implements AuthenticationHandler {
  private static final Logger LOGGER = LogManager.getLogger(OptionalJwtAuthHandler.class);
  private final JWTAuth jwtAuth;

  public OptionalJwtAuthHandler(JWTAuth jwtAuth) {
    this.jwtAuth = jwtAuth;
  }

  @Override
  public void handle(RoutingContext ctx) {
    LOGGER.info("OptionalJwtAuthHandler invoked");

    String token = BearerTokenExtractor.extract(ctx);
    if (token == null || token.isBlank()) {
      LOGGER.warn("Missing or invalid Authorization header");
      ctx.next();
      return;
    }

    jwtAuth
        .authenticate(new JsonObject().put("token", token))
        .onSuccess(
            user -> {
              ctx.setUser(user);
              ctx.next();
            })
        .onFailure(
            err -> {
              LOGGER.error("Authentication failed: {}", err.getMessage());
              ctx.fail(new DxUnauthorizedException("Invalid token: " + err.getMessage()));
            });
  }
}
