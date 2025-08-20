package org.cdpg.dx.auth.authentication.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.util.BearerTokenExtractor;

public class AAAJwtAuthHandler implements AuthenticationHandler {
  private static final Logger LOGGER = LogManager.getLogger(AAAJwtAuthHandler.class);
  private final JWTAuth jwtAuth;

  public AAAJwtAuthHandler(JWTAuth jwtAuth) {
      this.jwtAuth = jwtAuth;
  }

    @Override
    public void handle(RoutingContext ctx) {
        LOGGER.debug("Handling authentication for AAA JWT");
        String token = BearerTokenExtractor.extract(ctx);
        if (token == null || token.isBlank()) {
            LOGGER.warn("Missing or invalid Authorization header");
            ctx.put("auth_error", "Missing Bearer token in Authorization header");
            ctx.put("auth_failed", true);
            return;
        }

        jwtAuth.authenticate(new JsonObject().put("token", token))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        LOGGER.debug("Authentication successful for AAA JWT");
                        ctx.setUser(ar.result());
                        ctx.put("auth_failed", false);
                        ctx.next();
                    } else {
                        LOGGER.warn("Auth failed: {}", ar.cause().getMessage());
                        // do NOT call ctx.fail(ar.cause());
                        ctx.put("auth_failed", true);
                        ctx.put("auth_error", ar.cause().getMessage());
                    }
                });
    }

}
