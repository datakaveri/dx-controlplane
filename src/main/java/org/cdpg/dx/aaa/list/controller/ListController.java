package org.cdpg.dx.aaa.list.controller;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.LIST_AVAILABLE_FILTER;
import static org.cdpg.dx.aaa.common.Constants.RESULTS;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.list.service.ListService;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PostSearchRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class ListController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(ListController.class);
  private final URNGenerator urnGenerator;
  private final KeycloakUserService keycloakUserService;
  AuditingHandler auditingHandler;
  ListService listService;

  public ListController(
      AuditingHandler auditingHandler,
      ListService listService,
      KeycloakUserService keycloakUserService,
      URNGenerator urnGenerator) {
    this.auditingHandler = auditingHandler;
    this.listService = listService;
    this.urnGenerator = urnGenerator;
    this.keycloakUserService = keycloakUserService;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder
        .operation(LIST_AVAILABLE_FILTER)
        .handler(this::handleGetAvailableFilters)
        .handler(auditingHandler::handleApiAudit);
    LOGGER.debug("List Controller registered");
  }

  private void handleGetAvailableFilters(RoutingContext routingContext) {

    PostSearchRequestBuilder.fromRoutingContext(routingContext, keycloakUserService)
        .setAssetSearch(false)
        .setCountApi(false)
        .build() // Future<QueryDecoderRequestDTO>
        .compose(queryDecoder -> listService.getAvailableFilters(queryDecoder))
        .onSuccess(
            successHandler -> {
              ResponseBuilder.sendSuccess(
                  routingContext,
                  successHandler.getResponse().getJsonArray(RESULTS),
                  successHandler.getPaginationInfo(),
                  urnGenerator);
            })
        .onFailure(
            failureHandler -> {
              LOGGER.error(
                  "Failed to fetch activity logs: {}", failureHandler.getMessage(), failureHandler);
              routingContext.fail(failureHandler);
            });
  }
}
