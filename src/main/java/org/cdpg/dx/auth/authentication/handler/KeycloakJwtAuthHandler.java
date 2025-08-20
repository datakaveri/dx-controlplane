package org.cdpg.dx.auth.authentication.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.util.BearerTokenExtractor;

public record KeycloakJwtAuthHandler(JWTAuth jwtAuth) implements AuthenticationHandler {
    private static final Logger LOGGER = LogManager.getLogger(KeycloakJwtAuthHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        LOGGER.debug("Handling authentication for Keycloak JWT");
        String token = BearerTokenExtractor.extract(ctx);
        if (token == null || token.isBlank()) {
            LOGGER.warn("Missing or invalid Authorization header");
            ctx.next(); // Let next handler try
            return;
        }

        jwtAuth.authenticate(new JsonObject().put("token", token))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        LOGGER.debug("auth successful for Keycloak JWT");
                        ctx.setUser(ar.result());
                        ctx.put("auth_failed", false);
                        ctx.next();
                    } else {
                        LOGGER.warn("Auth failed: {}", ar.cause().getMessage());
                        ctx.put("auth_error", ar.cause().getMessage());
                        ctx.put("auth_failed", true);
                    }
                });
    }
}
