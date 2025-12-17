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
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;

public class CentralListController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(CentralListController.class);
  AuditingHandler auditingHandler;
  CentralListService centralListService;
  private final URNGenerator urnGenerator;

  public CentralListController(AuditingHandler auditingHandler,
                               CentralListService centralListService,
                               URNGenerator urnGenerator) {
    this.auditingHandler = auditingHandler;
    this.centralListService = centralListService;
    this.urnGenerator = urnGenerator;
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
    QueryDecoderRequestDTO queryDecoder =
      PostSearchRequestBuilder.fromRoutingContext(routingContext)
        .setAssetSearch(false)
        .setCountApi(false)
        .build();
    centralListService
      .getAvailableFilters(queryDecoder)
      .onSuccess(
        successHandler -> {
          ResponseBuilder.sendSuccess(
            routingContext,
            successHandler.getResponse().getJsonArray(RESULTS),
            successHandler.getPaginationInfo(),urnGenerator);
        })
      .onFailure(
        failureHandler -> {
          LOGGER.error(
            "Failed to fetch activity logs: {}", failureHandler.getMessage());
          routingContext.fail(failureHandler);
        });
  }
}
