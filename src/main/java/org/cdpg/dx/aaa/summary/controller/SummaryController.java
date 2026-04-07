package org.cdpg.dx.aaa.summary.controller;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.summary.service.SummaryService;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.response.ResponseBuilder;

import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_GET_DASHBOARD_USAGE_SUMMARY;

public class SummaryController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(SummaryController.class);

  private final SummaryService summaryService;
  private final URNGenerator urnGenerator;

  public SummaryController(SummaryService summaryService, URNGenerator urnGenerator) {
    this.summaryService = summaryService;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering SummaryController routes");
    builder.operation(OP_GET_DASHBOARD_USAGE_SUMMARY).handler(this::handleAggregateUsage);
  }

  private void handleAggregateUsage(RoutingContext ctx) {
    LOGGER.trace("handleAggregateUsage() method called");

    summaryService
        .getUsageSummaryForMainDashboard()
        .onSuccess(
            usageSummaries -> {
              LOGGER.info("Successfully retrieved usage summaries");
              ResponseBuilder.sendSuccess(ctx, usageSummaries, urnGenerator);
            })
        .onFailure(
            failure -> {
              LOGGER.error("Failed to retrieve usage summaries {}", failure.getMessage());
              ctx.fail(failure);
            });
  }
}
