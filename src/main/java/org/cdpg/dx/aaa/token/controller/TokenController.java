package org.cdpg.dx.aaa.token.controller;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.token.model.AccessTokenRequest;
import org.cdpg.dx.aaa.token.service.TokenService;
import org.cdpg.dx.aaa.token.util.AccessTokenRequestBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.response.ResponseBuilder;

public class TokenController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(TokenController.class);
  private final TokenService tokenService;
  private final URNGenerator urnGenerator;

  public TokenController(TokenService tokenService, URNGenerator urnGenerator) {
    this.tokenService = tokenService;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder.operation("post-auth-v2-token").handler(this::handleCreateToken);
  }

  private void handleCreateToken(RoutingContext ctx) {
    AccessTokenRequest request;
    try {
      request = AccessTokenRequestBuilder.fromContext(ctx);
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException(e.getMessage()));
      return;
    }

    tokenService
        .createToken(request)
        .onSuccess(token -> ResponseBuilder.sendSuccess(ctx, token, urnGenerator))
        .onFailure(
            err -> {
              LOGGER.error("Token creation failed: {}", err.getMessage());
              ctx.fail(err);
            });
  }
}
