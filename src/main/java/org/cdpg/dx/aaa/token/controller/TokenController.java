package org.cdpg.dx.aaa.token.controller;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.token.service.TokenService;

public class TokenController implements ApiController {

  private final TokenService tokenService;

  public TokenController(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder.operation("get-auth-v1-jwks").handler(this::retrievePublicKey);
    builder.operation("post-auth-v1-token").handler(this::handleCreateToken);
  }

  private void handleCreateToken(RoutingContext ctx) {
    String clientId = ctx.request().getHeader("clientId");
    String clientSecret = ctx.request().getHeader("clientSecret");

    tokenService
        .createToken(clientId, clientSecret)
        .onSuccess(
            token ->
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(token.encodePrettily()))
        .onFailure(err -> ctx.fail(401, new RuntimeException("Unauthorized: " + err.getMessage())));
  }

  private void retrievePublicKey(RoutingContext ctx) {

    JsonObject jwks = tokenService.generateJwks();

    if (jwks.isEmpty()) {
      ctx.fail(404, new RuntimeException("No public key found"));
    } else {
      ctx.response().putHeader("Content-Type", "application/json").end(jwks.encodePrettily());
    }
  }
}
