package org.cdpg.dx.auth.authentication.util;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

import java.util.List;

public record ChainedJwtAuthHandler(List<AuthenticationHandler> handlers) implements AuthenticationHandler {
    private static final Logger LOGGER = LogManager.getLogger(ChainedJwtAuthHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        verifyNext(ctx, 0);
    }

    private void verifyNext(RoutingContext ctx, int index) {
//        if (index >= handlers.size() && isOptional) {
//            LOGGER.debug("Authentication failed at all handlers, returning 401");
//            ctx.fail(new DxUnauthorizedException(ctx.get("auth_error"))); // no valid token
//            return;
//        }


        if (index >= handlers.size()) {
            LOGGER.debug("Authentication failed at all handlers, returning 401");
            ctx.fail(new DxUnauthorizedException(ctx.get("auth_error"))); // no valid token
            return;
        }

        AuthenticationHandler handler = handlers.get(index);
        LOGGER.debug("Handling authentication with handler {}", handler.getClass().getSimpleName());
        handler.handle(ctx);

        Boolean authFailed = ctx.get("auth_failed");
        if (authFailed) {
            LOGGER.warn("Authentication failed at handler {}", handler.getClass().getSimpleName());
            ctx.put("auth_failed", false);
            verifyNext(ctx, index + 1); // try next handler
        }
    }
}
