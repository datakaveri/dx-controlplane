package org.cdpg.dx.aaa.ActivityReport.controller;

import static org.cdpg.dx.auditing.v2.Constant.ActivityApiParamConstants.*;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.CREATED_AT;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.USER_ID;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_FIELD;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.ActivityReport.service.ActivityReportService;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auditing.v2.util.Util;
import org.cdpg.dx.auth.authorization.model.AuthorizationContext;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.ScopeRule;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;

public class ActivityReportController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(ActivityReportController.class);
  private final ActivityReportService reportService;

  public ActivityReportController(
      ActivityReportService reportService) {
    this.reportService = reportService;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder
        .operation("get-admin-report")
        .handler(
            AuthorizationHandler.forScopesWithContext(
                ScopeRule.platform(Scopes.USER_MANAGEMENT),
                ScopeRule.org(Scopes.ORG_USER_MANAGEMENT)))
        .handler(this::handleGenerateCsvForAdmin);
    builder
        .operation("get-consumer-report")
        .handler(AuthorizationHandler.forScopes(Scopes.DATA_ACCESS))
        .handler(this::handleGenerateCsvForConsumer);
  }

  private void handleGenerateCsvForAdmin(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    response
        .putHeader("Access-Control-Allow-Origin", "*")
        .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
        .putHeader("Content-Type", "text/csv")
        .putHeader("Content-Disposition", "attachment; filename=\"admin_report.csv\"")
        .setChunked(true);

    AuthorizationContext authCtx = routingContext.get(AuthorizationContext.KEY);
    Map<String, String> allowedFilters = Util.getAllowedFilterMapForAdmin(authCtx);
    Map<String, Object> additionalFilter = new HashMap<>(Util.getAdditionalFilters(authCtx));

    // A delegated (DID header) request has its subject swapped to the admin being acted as,
    // so scope the results to just this delegate's own actions rather than the full admin view.
    additionalFilter.putAll(Util.getDelegationFilters(routingContext));

    LOGGER.info("Admin auth level: {}, allowed filters: {}", authCtx.getLevel(), allowedFilters);

    PaginatedRequest request =
        PaginationRequestBuilder.from(routingContext)
            .allowedFiltersDbMap(allowedFilters)
            .additionalFilters(additionalFilter)
            .apiToDbMap(API_TO_DB_FIELD_MAP_V2)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(DEFAULT_SORTING_FIELD, DEFAULT_SORTING_ORDER)
            .allowedSortFields(ALLOWED_SORT_FIELDS_V2)
            .build();
    LOGGER.debug("PaginatedRequest created for handleGetAllActivityLogsForAdmin:  {}", request);

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
                  .handler(buffer -> response.write(buffer))
                  .endHandler(v -> response.end());
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to stream CSV", err);
              routingContext.fail(err);
            });
  }

  private void handleGenerateCsvForConsumer(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    response
        .putHeader("Access-Control-Allow-Origin", "*")
        .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
        .putHeader("Content-Type", "text/csv")
        .putHeader("Content-Disposition", "attachment; filename=\"consumer_report.csv\"")
        .setChunked(true);

    User user = routingContext.user();

    Map<String, Object> additionalFilters = new HashMap<>();
    additionalFilters.put(USER_ID, user.subject());

    // A delegated (DID header) request has its subject swapped to the delegator, so scope
    // the results to just this delegate's own actions rather than the delegator's full log.
    additionalFilters.putAll(Util.getDelegationFilters(routingContext));

    PaginatedRequest request =
        PaginationRequestBuilder.from(routingContext)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_CONSUMER_V2)
            .additionalFilters(additionalFilters)
            .apiToDbMap(API_TO_DB_FIELD_MAP_V2)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(DEFAULT_SORTING_FIELD, DEFAULT_SORTING_ORDER)
            .allowedSortFields(ALLOWED_SORT_FIELDS_V2)
            .build();

    reportService
        .streamConsumerCsvBatched(request)
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
                  .handler(buffer -> response.write(buffer))
                  .endHandler(v -> response.end());
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to stream CSV", err);
              routingContext.fail(err);
            });
  }
}
