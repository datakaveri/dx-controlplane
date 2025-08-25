package org.cdpg.dx.aaa.clientSecret.controller;

import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialService;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.response.ResponseBuilder;

public class ClientController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(ClientController.class);

  private final ClientcredetialService clientcredetialService;
  private final URNGenerator urnGenerator;

  public ClientController(ClientcredetialService clientcredetialService,URNGenerator urnGenerator) {
    this.clientcredetialService = clientcredetialService;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder.operation("post-create-client-secret").handler(this::createClientAndSecretHandler);
  }

  private void createClientAndSecretHandler(RoutingContext ctx) {
    User user = ctx.user();
    if (user == null || user.subject() == null) {
      LOGGER.error("User or user subject is null in context");
      ctx.fail(new DxUnauthorizedException("User or user subject is null in context"));
      return;
    }

    UUID userId;
    try {
      userId = UUID.fromString(user.subject());
    } catch (IllegalArgumentException e) {
      LOGGER.error("Invalid user subject for UUID: {}", user.subject(), e);
      ctx.fail(new DxBadRequestException("Invalid user subject for UUID"));
      return;
    }

    clientcredetialService
      .createClientIdAndClientSecret(userId)
      .onSuccess(clientCredentials -> ResponseBuilder.sendSuccess(ctx, clientCredentials,urnGenerator))
      .onFailure(
        err -> {
          LOGGER.error("Failed to create client credentials: {}", err.getMessage(), err);
          ctx.fail(err);
        });
  }
}
