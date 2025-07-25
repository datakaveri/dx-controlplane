package org.cdpg.dx.auth.authentication.handler;


import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.util.BearerTokenExtractor;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

public class KeycloakJwtAuthHandler implements AuthenticationHandler {
    private static final Logger LOGGER = LogManager.getLogger(KeycloakJwtAuthHandler.class);
    private final JWTAuth jwtAuth;

    public KeycloakJwtAuthHandler(JWTAuth jwtAuth) {
        this.jwtAuth = jwtAuth;
    }

    @Override
    public void handle(RoutingContext ctx) {

        String token = BearerTokenExtractor.extract(ctx);
        if (token == null || token.isBlank()) {
            LOGGER.warn("Missing or invalid Authorization header");
            ctx.fail(new DxUnauthorizedException("Missing Bearer token"));
            return;
        }


        jwtAuth.authenticate(new JsonObject().put("token", token))
                .onSuccess(user -> {
                    ctx.setUser(user);
                    ctx.next();
                })
                .onFailure(err -> {
                    LOGGER.warn("Authentication failed: {}", err.getMessage());
                    ctx.fail(new DxUnauthorizedException("Unauthorized"));
                });
    }
}

