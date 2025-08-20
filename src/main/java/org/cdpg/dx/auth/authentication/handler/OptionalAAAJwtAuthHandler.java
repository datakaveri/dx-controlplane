package org.cdpg.dx.auth.authentication.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.util.BearerTokenExtractor;

public class OptionalAAAJwtAuthHandler implements AuthenticationHandler {
  private static final Logger LOGGER = LogManager.getLogger(OptionalAAAJwtAuthHandler.class);
  private final JWTAuth jwtAuth;

  public OptionalAAAJwtAuthHandler(JWTAuth jwtAuth) {
    this.jwtAuth = jwtAuth;
  }

  @Override
  public void handle(RoutingContext ctx) {
    LOGGER.info("OptionalAAAJwtAuthHandler invoked");

    String token = BearerTokenExtractor.extract(ctx);
    if (token == null || token.isBlank()) {
      LOGGER.warn("Missing or invalid Authorization header");
      ctx.next();
      return;
    }

      jwtAuth.authenticate(new JsonObject().put("token", token))
              .onComplete(ar -> {
                  if (ar.succeeded()) {
                      LOGGER.debug("Authentication successful for Optional AAA JWT");
                      ctx.setUser(ar.result());
                      ctx.put("auth_failed", false);
                      ctx.next();
                  } else {
                      LOGGER.warn("Auth failed: {}", ar.cause().getMessage());
                      ctx.put("auth_failed", true);
                      ctx.put("auth_error", ar.cause().getMessage());
                  }
              });
  }
}
