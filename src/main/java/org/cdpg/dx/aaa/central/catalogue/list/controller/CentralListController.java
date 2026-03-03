package org.cdpg.dx.aaa.central.catalogue.list.controller;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.LIST_AVAILABLE_CENTRAL_CAT_FILTERS;
import static org.cdpg.dx.aaa.common.Constants.RESULTS;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.central.catalogue.list.service.CentralListService;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PostSearchRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class CentralListController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(CentralListController.class);
  private final URNGenerator urnGenerator;
  AuditingHandler auditingHandler;
  CentralListService centralListService;
  KeycloakUserService keycloakUserService;

  public CentralListController(
      AuditingHandler auditingHandler,
      CentralListService centralListService,
      KeycloakUserService keycloakUserService,
      URNGenerator urnGenerator) {
    this.auditingHandler = auditingHandler;
    this.centralListService = centralListService;
    this.urnGenerator = urnGenerator;
    this.keycloakUserService = keycloakUserService;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder
        .operation(LIST_AVAILABLE_CENTRAL_CAT_FILTERS)
        .handler(this::handleGetAvailableFilters)
        .handler(auditingHandler::handleApiAudit);
    LOGGER.debug("List Controller registered");
  }

  private void handleGetAvailableFilters(RoutingContext routingContext) {

    PostSearchRequestBuilder.fromRoutingContext(routingContext, keycloakUserService)
        .setAssetSearch(false)
        .setCountApi(false)
        .build() // now returns Future<QueryDecoderRequestDTO>
        .compose(queryDecoder -> centralListService.getAvailableFilters(queryDecoder))
        .onSuccess(
            successHandler ->
                ResponseBuilder.sendSuccess(
                    routingContext,
                    successHandler.getResponse().getJsonArray(RESULTS),
                    successHandler.getPaginationInfo(),
                    urnGenerator))
        .onFailure(
            failureHandler -> {
              LOGGER.error("Failed to fetch activity logs: {}", failureHandler.getMessage());
              routingContext.fail(failureHandler);
            });
  }
}
