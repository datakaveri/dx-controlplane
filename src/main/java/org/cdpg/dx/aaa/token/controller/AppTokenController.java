package org.cdpg.dx.aaa.token.controller;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.token.model.AccessTokenRequest;
import org.cdpg.dx.aaa.token.model.AppTokenRequest;
import org.cdpg.dx.aaa.token.service.AppTokenService;
import org.cdpg.dx.aaa.token.util.AccessTokenRequestBuilder;
import org.cdpg.dx.aaa.token.util.AppTokenRequestBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.response.ResponseBuilder;

import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_POST_APP_TOKEN;

public class AppTokenController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(AppTokenController.class);
  private final AppTokenService appTokenService;
  private final URNGenerator urnGenerator;

  public AppTokenController(AppTokenService appTokenService, URNGenerator urnGenerator) {
    this.appTokenService = appTokenService;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {

    builder.operation(OP_POST_APP_TOKEN).handler(this::handleCreateToken);
  }

  private void handleCreateToken(RoutingContext ctx) {
    AppTokenRequest request;
    try {
      request = AppTokenRequestBuilder.fromContext(ctx);
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException(e.getMessage()));
      return;
    }

    appTokenService
        .createToken(request)
        .onSuccess(token -> ResponseBuilder.sendSuccess(ctx, token, urnGenerator))
        .onFailure(
            err -> {
              LOGGER.error("App token creation failed: {}", err.getMessage());
              ctx.fail(err);
            });
  }
}
