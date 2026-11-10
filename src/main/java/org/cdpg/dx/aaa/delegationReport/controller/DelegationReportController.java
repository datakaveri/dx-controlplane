package org.cdpg.dx.aaa.delegationReport.controller;

import static org.cdpg.dx.aaa.delegation.util.Constants.API_TO_DB_DELEGATION_GRANT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CREATED_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_DELEGATOR_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_UPDATED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegationReport.service.DelegationReportService;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;

public class DelegationReportController implements ApiController {

  public static final String GET_DELEGATION_REPORT_FOR_DELEGATOR_API =
      "get-auth-v2-delegation-delegator-report";
  private static final Logger LOGGER = LogManager.getLogger(DelegationReportController.class);
  private final DelegationReportService delegationReportService;

  public DelegationReportController(DelegationReportService delegationReportService) {
    this.delegationReportService = delegationReportService;
  }

  @Override
  public void register(RouterBuilder builder) {

    Handler<RoutingContext> delegatorAccess = AuthorizationHandler.forRoles(DxRole.CONSUMER);

    builder
        .operation(GET_DELEGATION_REPORT_FOR_DELEGATOR_API)
        .handler(delegatorAccess)
        .handler(this::handleGenerateDelegationCsv);
  }

  private void handleGenerateDelegationCsv(RoutingContext routingContext) {

    HttpServerResponse response = routingContext.response();

    response
        .putHeader("Access-Control-Allow-Origin", "*")
        .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        .putHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        .putHeader("Content-Type", "text/csv")
        .putHeader("Content-Disposition", "attachment; filename=\"delegation_report.csv\"")
        .setChunked(true);

    Map<String, Object> additionalFilters =
        Map.of(DB_DELEGATOR_ID, routingContext.user().subject());

    Set<String> allowedSortFields = Set.of(DB_CREATED_AT, DB_UPDATED_AT, DB_STATUS);

    PaginatedRequest request =
        PaginationRequestBuilder.from(routingContext)
            .apiToDbMap(API_TO_DB_DELEGATION_GRANT)
            .additionalFilters(additionalFilters)
            .defaultSort(DB_CREATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(allowedSortFields)
            .build();

    LOGGER.info("PaginatedRequest created for delegation report: {}", request);

    delegationReportService
        .streamDelegationCsvBatched(request)
        .onSuccess(
            csvStream -> {
              if (csvStream == null) {
                response.end();
                return;
              }

              csvStream
                  .exceptionHandler(
                      err -> {
                        LOGGER.error("Failed to stream delegation CSV", err);
                        routingContext.fail(err);
                      })
                  .handler(response::write)
                  .endHandler(v -> response.end());
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to generate delegation CSV", err);
              routingContext.fail(err);
            });
  }
}
