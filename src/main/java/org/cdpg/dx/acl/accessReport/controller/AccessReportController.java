package org.cdpg.dx.acl.accessReport.controller;

import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_ACCESS_REQUEST_REPORT_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_ACCESS_REQUEST_REPORT_FOR_ORG_ADMIN_API;
import static org.cdpg.dx.acl.accessRequest.util.Constants.API_TO_DB_MAP;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.*;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.accessReport.service.ReportService;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.model.Scopes;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;

public class AccessReportController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(AccessReportController.class);
  private final ReportService reportService;

  public AccessReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @Override
  public void register(RouterBuilder builder) {
    Handler<RoutingContext> providerAccess = AuthorizationHandler.forScopes(Scopes.OWN_ASSET_MANAGEMENT);
    Handler<RoutingContext> orgAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_ASSET_MANAGEMENT);

    builder
        .operation(GET_ACCESS_REQUEST_REPORT_API)
        .handler(providerAccess)
        .handler(this::handleGenerateCsvForProvider);

    builder
        .operation(GET_ACCESS_REQUEST_REPORT_FOR_ORG_ADMIN_API)
        .handler(orgAdminAccess)
        .handler(this::handleGenerateCsvForOrgAdmin);
  }

  private void handleGenerateCsvForProvider(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    response
        .putHeader("Access-Control-Allow-Origin", "*")
        .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
        .putHeader("Content-Type", "text/csv")
        .putHeader(
            "Content-Disposition", "attachment; filename=\"provider_access_request_report.csv\"")
        .setChunked(true);

    Map<String, String> allowedFilters =
        Map.of("requestStatus", DB_STATUS, "assetType", DB_ASSET_TYPE);
    Map<String, Object> additionalFilters = Map.of(DB_PROVIDER_ID, routingContext.user().subject());
    Set<String> allowedTimeFields = Set.of(DB_CREATED_AT, DB_UPDATED_AT, DB_EXPIRY_AT);
    Set<String> allowedSortFields = API_TO_DB_MAP.keySet();

    PaginatedRequest request =
        PaginationRequestBuilder.from(routingContext)
            .allowedFiltersDbMap(allowedFilters)
            .apiToDbMap(API_TO_DB_MAP)
            .additionalFilters(additionalFilters)
            .allowedTimeFields(allowedTimeFields)
            .defaultTimeField(DB_CREATED_AT)
            .defaultSort(DB_UPDATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(allowedSortFields)
            .build();

    LOGGER.info("PaginatedRequest created for handleGenerateCsvForProvider:  {}", request);
    reportService
        .streamAdminCsvBatched(request)
        .onSuccess(
            csvStream -> {
              if (csvStream == null) {
                response.end();
                return;
              }
              csvStream
                  .exceptionHandler(
                      err -> {
                        LOGGER.error("Failed to stream CSV", err);
                        routingContext.fail(err);
                      })
                  .handler(response::write)
                  .endHandler(v -> response.end());
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to stream CSV", err);
              routingContext.fail(err);
            });
  }

  private void handleGenerateCsvForOrgAdmin(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    response
        .putHeader("Access-Control-Allow-Origin", "*")
        .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
        .putHeader("Content-Type", "text/csv")
        .putHeader(
            "Content-Disposition", "attachment; filename=\"org_admin_access_request_report.csv\"")
        .setChunked(true);

    String organisationId = RoutingContextHelper.fromPrincipal(routingContext).organisationId();
    Map<String, String> allowedFilters =
        Map.of("requestStatus", DB_STATUS, "assetType", DB_ASSET_TYPE);
    Map<String, Object> additionalFilters = Map.of(DB_ASSET_ORGANIZATION_ID, organisationId);
    Set<String> allowedTimeFields = Set.of(DB_CREATED_AT, DB_UPDATED_AT, DB_EXPIRY_AT);
    Set<String> allowedSortFields = API_TO_DB_MAP.keySet();

    PaginatedRequest request =
        PaginationRequestBuilder.from(routingContext)
            .allowedFiltersDbMap(allowedFilters)
            .apiToDbMap(API_TO_DB_MAP)
            .additionalFilters(additionalFilters)
            .allowedTimeFields(allowedTimeFields)
            .defaultTimeField(DB_CREATED_AT)
            .defaultSort(DB_UPDATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(allowedSortFields)
            .build();

    LOGGER.info("PaginatedRequest created for handleGenerateCsvForOrgAdmin:  {}", request);
    reportService
        .streamAdminCsvBatched(request)
        .onSuccess(
            csvStream -> {
              if (csvStream == null) {
                response.end();
                return;
              }
              csvStream
                  .exceptionHandler(
                      err -> {
                        LOGGER.error("Failed to stream CSV", err);
                        routingContext.fail(err);
                      })
                  .handler(response::write)
                  .endHandler(v -> response.end());
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to stream CSV", err);
              routingContext.fail(err);
            });
  }
}
