package org.cdpg.dx.aaa.central.catalogue.search.controller;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.CENTRAL_POST_COUNT_SEARCH;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.CENTRAL_POST_SEARCH;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.GET_ASSET_SEARCH;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.GET_ORG_ASSETS;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.GET_ORG_ASSETS_VTH_FILTERS;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.GET_PLATFORM_ASSETS;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.GET_PLATFORM_ASSETS_VTH_FILTERS;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.POST_ASSET_SEARCH;
import static org.cdpg.dx.aaa.common.Constants.RESULTS;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.central.catalogue.search.service.CentralSearchService;
import org.cdpg.dx.aaa.common.CheckIfTokenPresent;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.GetSearchRequestBuilder;
import org.cdpg.dx.common.request.OrganisationAssetRequestBuilder;
import org.cdpg.dx.common.request.PlatformAssetRequestBuilder;
import org.cdpg.dx.common.request.PostSearchRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class CentralSearchController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(CentralSearchController.class);
  private static final CheckIfTokenPresent TOKEN_CHECK = new CheckIfTokenPresent();
  private final CentralSearchService centralSearchService;
  private final KeycloakUserService keycloakUserService;
  private final AuditingHandler auditingHandler;
  private final URNGenerator urnGenerator;
  Handler<RoutingContext> orgAdminAccessHandler = AuthorizationHandler.forRoles(DxRole.ORG_ADMIN);
  Handler<RoutingContext> pfAdminAccessHandler = AuthorizationHandler.forRoles(DxRole.COS_ADMIN);

  public CentralSearchController(
      CentralSearchService centralSearchService,
      KeycloakUserService keycloakUserService,
      AuditingHandler auditingHandler,
      URNGenerator urnGenerator) {
    this.centralSearchService = centralSearchService;
    this.keycloakUserService = keycloakUserService;
    this.auditingHandler = auditingHandler;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder
        .operation(CENTRAL_POST_SEARCH)
        .handler(this::handleSearch)
        .handler(auditingHandler::handleApiAudit);

    builder
        .operation(CENTRAL_POST_COUNT_SEARCH)
        .handler(this::handleCount)
        .handler(auditingHandler::handleApiAudit);
    //
    //    builder
    //        .operation(POST_ASSET_SEARCH)
    //        .handler(TOKEN_CHECK)
    //        .handler(this::handlePostAsset)
    //        .handler(auditingHandler::handleApiAudit);
    //
    //    builder
    //        .operation(GET_ASSET_SEARCH)
    //        .handler(TOKEN_CHECK)
    //        .handler(this::handleGetAsset)
    //        .handler(auditingHandler::handleApiAudit);
    //
    //    builder.operation(GET_ORG_ASSETS)
    //        .handler(TOKEN_CHECK)
    //        .handler(orgAdminAccessHandler)
    //        .handler(this::handleOrganisationGetItems)
    //        .handler(auditingHandler::handleApiAudit);
    //
    //    builder.operation(GET_ORG_ASSETS_VTH_FILTERS)
    //        .handler(TOKEN_CHECK)
    //        .handler(orgAdminAccessHandler)
    //        .handler(this::handleOrganisationAssetsVthFilters)
    //        .handler(auditingHandler::handleApiAudit);
    //
    //    builder.operation(GET_PLATFORM_ASSETS)
    //        .handler(TOKEN_CHECK)
    //        .handler(pfAdminAccessHandler)
    //        .handler(this::handleGetPlatformItems)
    //        .handler(auditingHandler::handleApiAudit);
    //
    //    builder.operation(GET_PLATFORM_ASSETS_VTH_FILTERS)
    //        .handler(TOKEN_CHECK)
    //        .handler(pfAdminAccessHandler)
    //        .handler(this::handlePlatformAssetsVthFilters)
    //        .handler(auditingHandler::handleApiAudit);

    LOGGER.debug(
        "Registered CentralSearchController operations: {}, {}, {}, {}, {}, {}, {}, {}, {}",
        CENTRAL_POST_SEARCH,
        CENTRAL_POST_COUNT_SEARCH,
        POST_ASSET_SEARCH,
        GET_ASSET_SEARCH,
        GET_PLATFORM_ASSETS,
        GET_ORG_ASSETS,
        GET_ORG_ASSETS_VTH_FILTERS,
        GET_PLATFORM_ASSETS,
        GET_PLATFORM_ASSETS_VTH_FILTERS);
  }

  private void handleGetPlatformItems(RoutingContext ctx) {
    LOGGER.debug("Received GET Platform assets request on '{}'", GET_PLATFORM_ASSETS);
    try {
      QueryDecoderRequestDTO queryDecoder =
          PlatformAssetRequestBuilder.fromRoutingContext(ctx).build();
      processSearchRequest(ctx, queryDecoder);
    } catch (Exception e) {
      LOGGER.error("Error processing pf_admin asset request: {}", e.getMessage());
      ctx.fail(e);
    }
  }

  private void handlePlatformAssetsVthFilters(RoutingContext ctx) {

    LOGGER.debug("Received POST request on at search'{}'", GET_PLATFORM_ASSETS_VTH_FILTERS);

    PostSearchRequestBuilder.fromRoutingContext(ctx, keycloakUserService)
        .setPlatformAssetSearch(true)
        .setOrgAssetsSearch(false)
        .setAssetSearch(false)
        .setCountApi(false)
        .build() // Future<QueryDecoderRequestDTO>
        .onSuccess(
            queryDecoder -> {
              processSearchRequest(ctx, queryDecoder);
            })
        .onFailure(
            e -> {
              LOGGER.error("Error processing search request: {}", e.getMessage(), e);
              ctx.fail(e);
            });
  }

  private void handleOrganisationGetItems(RoutingContext ctx) {
    LOGGER.debug("Received GET Asset request on '{}'", GET_ORG_ASSETS);
    try {
      QueryDecoderRequestDTO queryDecoder =
          OrganisationAssetRequestBuilder.fromRoutingContext(ctx).build();
      processSearchRequest(ctx, queryDecoder);
    } catch (Exception e) {
      LOGGER.error("Error processing asset request: {}", e.getMessage());
      ctx.fail(e);
    }
  }

  private void handleOrganisationAssetsVthFilters(RoutingContext ctx) {

    LOGGER.debug("Received POST request on at search'{}'", GET_ORG_ASSETS_VTH_FILTERS);

    PostSearchRequestBuilder.fromRoutingContext(ctx, keycloakUserService)
        .setPlatformAssetSearch(false)
        .setOrgAssetsSearch(true)
        .setAssetSearch(false)
        .setCountApi(false)
        .build() // Future<QueryDecoderRequestDTO>
        .onSuccess(
            queryDecoder -> {
              processSearchRequest(ctx, queryDecoder);
            })
        .onFailure(
            e -> {
              LOGGER.error("Error processing search request: {}", e.getMessage(), e);
              ctx.fail(e);
            });
  }

  private void handleSearch(RoutingContext ctx) {

    LOGGER.debug("Received POST request on at search'{}'", CENTRAL_POST_SEARCH);

    PostSearchRequestBuilder.fromRoutingContext(ctx, keycloakUserService)
        .setPlatformAssetSearch(false)
        .setOrgAssetsSearch(false)
        .setAssetSearch(false)
        .setCountApi(false)
        .build() // Future<QueryDecoderRequestDTO>
        .onSuccess(
            queryDecoder -> {
              processSearchRequest(ctx, queryDecoder);
            })
        .onFailure(
            e -> {
              LOGGER.error("Error processing search request: {}", e.getMessage(), e);
              ctx.fail(e);
            });
  }

  private void handlePostAsset(RoutingContext ctx) {

    LOGGER.debug("Received POST Asset request on '{}'", POST_ASSET_SEARCH);

    PostSearchRequestBuilder.fromRoutingContext(ctx, keycloakUserService)
        .setPlatformAssetSearch(false)
        .setOrgAssetsSearch(false)
        .setAssetSearch(true)
        .setCountApi(false)
        .build() // Future<QueryDecoderRequestDTO>
        .onSuccess(
            queryDecoder -> {
              processSearchRequest(ctx, queryDecoder);
            })
        .onFailure(
            e -> {
              LOGGER.error("Error processing asset request: {}", e.getMessage(), e);
              ctx.fail(e);
            });
  }

  private void handleGetAsset(RoutingContext ctx) {
    LOGGER.debug("Received GET Asset request on '{}'", GET_ASSET_SEARCH);
    try {
      QueryDecoderRequestDTO queryDecoder =
          GetSearchRequestBuilder.fromRoutingContext(ctx).setAssetSearch(true).build();
      processSearchRequest(ctx, queryDecoder);
    } catch (Exception e) {
      LOGGER.error("Error processing asset request: {}", e.getMessage());
      ctx.fail(e);
    }
  }

  private void handleCount(RoutingContext ctx) {

    LOGGER.debug("Received POST Count request on '{}'", CENTRAL_POST_COUNT_SEARCH);

    PostSearchRequestBuilder.fromRoutingContext(ctx, keycloakUserService)
        .setPlatformAssetSearch(false)
        .setOrgAssetsSearch(false)
        .setAssetSearch(false)
        .setCountApi(true)
        .build() // Future<QueryDecoderRequestDTO>
        .compose(centralSearchService::postCount)
        .onSuccess(
            response ->
                ResponseBuilder.sendSuccess(
                    ctx, response.getResponse().getJsonArray(RESULTS), urnGenerator))
        .onFailure(
            err -> {
              LOGGER.error("Count request failed: {}", err.getMessage(), err);
              ctx.fail(err);
            });
  }

  private void processSearchRequest(RoutingContext ctx, QueryDecoderRequestDTO queryDecoder) {
    centralSearchService
        .postSearch(queryDecoder)
        .onSuccess(
            result ->
                ResponseBuilder.sendSuccess(
                    ctx,
                    result.getElasticsearchResponses(),
                    result.getPaginationInfo(),
                    urnGenerator))
        .onFailure(
            err -> {
              LOGGER.error("Search request failed: {}", err.getMessage());
              ctx.fail(err);
            });
  }
}
