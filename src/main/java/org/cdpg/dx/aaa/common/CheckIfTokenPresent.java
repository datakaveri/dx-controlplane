package org.cdpg.dx.aaa.common;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.util.BearerTokenExtractor;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

public final class CheckIfTokenPresent implements Handler<RoutingContext> {
  public static final CheckIfTokenPresent INSTANCE = new CheckIfTokenPresent();
  private static final Logger LOGGER = LogManager.getLogger(CheckIfTokenPresent.class);
  private static final String MISSING_TOKEN_MSG = "Missing or invalid Authorization header";
  private static final String UNAUTHORIZED_MSG = "Missing Bearer token";

  @Override
  public void handle(RoutingContext ctx) {
    if (tokenAbsent(ctx)) {
      LOGGER.warn(MISSING_TOKEN_MSG);
      ctx.fail(new DxUnauthorizedException(UNAUTHORIZED_MSG));
      return;
    }
    ctx.next();
  }

  private boolean tokenAbsent(RoutingContext ctx) {
    String token = BearerTokenExtractor.extract(ctx);
    return token == null || token.isBlank();
  }
}
