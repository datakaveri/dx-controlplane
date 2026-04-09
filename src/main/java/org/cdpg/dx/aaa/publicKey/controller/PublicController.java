package org.cdpg.dx.aaa.publicKey.controller;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.publicKey.service.PublicService;

public class PublicController implements ApiController {

  private final PublicService publicService;

  public PublicController(PublicService publicService) {
    this.publicService = publicService;
  }

  @Override
  public void register(RouterBuilder builder) {

    builder.operation("get-auth-v2-jwks").handler(this::retrievePublicKey);
  }

  private void retrievePublicKey(RoutingContext ctx) {

    JsonObject jwks = publicService.generateJwks();

    if (jwks.isEmpty()) {
      ctx.fail(404, new RuntimeException("No public key found"));
    } else {
      ctx.response().putHeader("Content-Type", "application/json").end(jwks.encodePrettily());
    }
  }
}
