package org.cdpg.dx.aaa.list.controller;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.LIST_AVAILABLE_FILTER;
import static org.cdpg.dx.aaa.common.Constants.RESULTS;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.list.service.ListService;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.request.PostSearchRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;

public class ListController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(ListController.class);
  AuditingHandler auditingHandler;
  ListService listService;

  public ListController(AuditingHandler auditingHandler, ListService listService) {
    this.auditingHandler = auditingHandler;
    this.listService = listService;
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
    QueryDecoderRequestDTO queryDecoder =
        PostSearchRequestBuilder.fromRoutingContext(routingContext)
            .setAssetSearch(false)
            .setCountApi(false)
            .build();
    listService
        .getAvailableFilters(queryDecoder)
        .onSuccess(
            successHandler -> {
              ResponseBuilder.sendSuccess(
                  routingContext,
                  successHandler.getResponse().getJsonArray(RESULTS),
                  successHandler.getPaginationInfo());
            })
        .onFailure(
            failureHandler -> {
              LOGGER.error(
                  "Failed to fetch activity logs: {}", failureHandler.getMessage(), failureHandler);
              routingContext.fail(failureHandler);
            });
  }
}
