package org.cdpg.dx.aaa.shareAssets.controller;

import static org.cdpg.dx.aaa.shareAssets.util.Constants.GET_SHARED_ASSETS_API;
import static org.cdpg.dx.aaa.shareAssets.util.Constants.GET_SHARED_ASSETS_WITH_ME_API;
import static org.cdpg.dx.aaa.shareAssets.util.Constants.REVOKE_SHARED_ASSETS_API;
import static org.cdpg.dx.aaa.shareAssets.util.Constants.SHARE_ASSETS_API;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.shareAssets.dao.model.VisibilityEntity;
import org.cdpg.dx.aaa.shareAssets.handler.ItemExistenceCheck;
import org.cdpg.dx.aaa.shareAssets.service.VisibilityService;
import org.cdpg.dx.aaa.shareAssets.service.model.ShareRequest;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class VisibilityController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(VisibilityController.class);

  private final VisibilityService visibilityService;
  private final URNGenerator urnGenerator;
  private final ItemExistenceCheck itemExistenceCheck;
  private final KeycloakUserService keycloakUserService;

  public VisibilityController(
      VisibilityService visibilityService,
      ElasticsearchService elasticsearchService,
      KeycloakUserService keycloakUserService,
      String docIndex,
      URNGenerator urnGenerator) {

    this.visibilityService = visibilityService;
    this.urnGenerator = urnGenerator;
    itemExistenceCheck = new ItemExistenceCheck(elasticsearchService, docIndex);
    this.keycloakUserService = keycloakUserService;
  }

  @Override
  public void register(RouterBuilder builder) {
    Handler<RoutingContext> providerAccess =
        AuthorizationHandler.forScopes(Scopes.OWN_ASSET_MANAGEMENT);
    Handler<RoutingContext> selfAccess = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);

    builder
        .operation(SHARE_ASSETS_API)
        .handler(providerAccess)
        .handler(itemExistenceCheck)
        .handler(this::shareAssetsHandler);

    builder
        .operation(REVOKE_SHARED_ASSETS_API)
        .handler(providerAccess)
        .handler(this::revokeAssetsHandler);

    builder
        .operation(GET_SHARED_ASSETS_API)
        .handler(providerAccess)
        .handler(this::getSharedAssetsHandler);

    builder.operation(GET_SHARED_ASSETS_WITH_ME_API)
        .handler(selfAccess)
        .handler(this::getSharedAssetsWithMeHandler);
  }

  private void getSharedAssetsWithMeHandler(RoutingContext ctx) {

    DxUser user = RoutingContextHelper.fromPrincipal(ctx);

    UUID userId = UUID.fromString(user.sub().toString());

    keycloakUserService
        .getUserById(userId)
        .compose(kcUser -> visibilityService.getAssetsSharedWithMe(userId, kcUser.organisationId()))
        .onSuccess(
            result ->
                ResponseBuilder.sendSuccess(
                    ctx, result.stream().map(VisibilityEntity::toJson).toList(), urnGenerator))
        .onFailure(ctx::fail);
  }

  private void shareAssetsHandler(RoutingContext ctx) {

    LOGGER.info("Handling share assets request");

    ShareRequest request = ShareRequest.fromJson(ctx.body().asJsonObject());

    DxUser user = RoutingContextHelper.fromPrincipal(ctx);

    visibilityService
        .shareAssets(
            request.getItemId(),
            request.getShareType(),
            UUID.fromString(user.sub().toString()),
            request.getIds())
        .onSuccess(
            res -> {
              LOGGER.info(
                  "Assets shared successfully for item {} by user {}",
                  request.getItemId(),
                  user.sub());

              ResponseBuilder.sendSuccess(ctx, "Assets shared successfully", urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed sharing assets for item {} : {}",
                  request.getItemId(),
                  err.getMessage(),
                  err);

              ctx.fail(err);
            });
  }

  private void revokeAssetsHandler(RoutingContext ctx) {

    LOGGER.info("Handling revoke shared assets request");

    ShareRequest request = ShareRequest.fromJson(ctx.body().asJsonObject());

    visibilityService
        .revokeAssets(request.getItemId(), request.getShareType(), request.getIds())
        .onSuccess(
            res -> {
              LOGGER.info("Shared access revoked successfully for item {}", request.getItemId());

              ResponseBuilder.sendSuccess(ctx, "Shared access revoked successfully", urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed revoking shared access for item {} : {}",
                  request.getItemId(),
                  err.getMessage(),
                  err);

              ctx.fail(err);
            });
  }

  private void getSharedAssetsHandler(RoutingContext ctx) {

    LOGGER.info("Handling get shared assets request");

    String itemId = ctx.queryParams().get("itemId");

    visibilityService
        .getVisibilityDetails(UUID.fromString(itemId))
        .onSuccess(
            result -> {
              LOGGER.info("Fetched visibility details successfully for item {}", itemId);

              ResponseBuilder.sendSuccess(
                  ctx, result.stream().map(VisibilityEntity::toJson).toList(), urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed fetching visibility details for item {} : {}",
                  itemId,
                  err.getMessage(),
                  err);

              ctx.fail(err);
            });
  }
}
