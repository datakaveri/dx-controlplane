package org.cdpg.dx.aaa.token.controller;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.token.service.TokenService;
import org.cdpg.dx.common.response.ResponseBuilder;

public class TokenController implements ApiController {

  private final TokenService tokenService;

  public TokenController(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder.operation("post-auth-v1-token").handler(this::handleCreateToken);
  }

  private void handleCreateToken(RoutingContext ctx) {
    String clientId = ctx.request().getHeader("clientId");
    String clientSecret = ctx.request().getHeader("clientSecret");

    tokenService
        .createToken(clientId, clientSecret)
        .onSuccess(
            token -> {
              ResponseBuilder.sendSuccess(ctx, token);
            })
        .onFailure(ctx::fail);
  }
}
