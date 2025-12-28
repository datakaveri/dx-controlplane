package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.orgReport.service.OrganizationCreateReportService;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.util.RequestHelper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.cdpg.dx.aaa.credit.util.Constants.ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST;
import static org.cdpg.dx.aaa.credit.util.Constants.API_TO_DB_CREDIT_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class OrganizationReportHandler {

  private static final Logger LOGGER = LogManager.getLogger(OrganizationReportHandler.class);

  /* =========================
   * HTTP / CSV constants
   * ========================= */

  private static final String HEADER_ACAO = "Access-Control-Allow-Origin";
  private static final String HEADER_ACAH = "Access-Control-Allow-Headers";
  private static final String HEADER_ACAM = "Access-Control-Allow-Methods";
  private static final String HEADER_CONTENT_TYPE = "Content-Type";
  private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";

  private static final String HEADER_ALLOW_HEADERS_VALUE = "Content-Type, Authorization";
  private static final String HEADER_ALLOW_METHODS_VALUE = "GET, POST, PUT, DELETE, OPTIONS";

  private static final String CONTENT_TYPE_CSV = "text/csv";
  private static final String CONTENT_DISPOSITION_FMT = "attachment; filename=\"%s\"";

  private static final String PATH_PARAM_ORGANIZATION_ID = "id";

  /* =========================
   * Dependencies
   * ========================= */

  private final OrganizationCreateReportService reportService;

  public OrganizationReportHandler(OrganizationCreateReportService reportService) {
    this.reportService = reportService;
  }

  /* =========================
   * Public handlers
   * ========================= */

  public void getOrganizationCreateReport(RoutingContext ctx) {

    LOGGER.info("Org create request report initiated");

    PaginatedRequest request =
        buildRequest(
            ctx,
            ALLOWED_FILTER_MAP_FOR_ORG_CREATE_REQUEST,
            API_TO_DB_ORG_CREATE_REQUEST,
            Set.of(CREATED_AT),
            CREATED_AT,
            API_TO_DB_ORG_CREATE_REQUEST.keySet(),
            null);

    reportService
        .streamAdminCsvBatchedCreateRequest(request)
        .onSuccess(
            csv ->
                streamCsv(ctx, csv, "org_create_request_report.csv", "ORG_CREATE_REQUEST_REPORT"))
        .onFailure(ctx::fail);
  }

  public void getOrganizationJoinReport(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, PATH_PARAM_ORGANIZATION_ID);
    LOGGER.info("Org join request report initiated | orgId={}", orgId);

    PaginatedRequest request =
        buildRequest(
            ctx,
            ALLOWED_FILTER_MAP_FOR_ORG_JOIN_REQUEST,
            API_TO_DB_ORG_JOIN_REQUEST,
            Set.of(REQUESTED_AT),
            REQUESTED_AT,
            API_TO_DB_ORG_JOIN_REQUEST.keySet(),
            Map.of(ORGANIZATION_ID, orgId.toString()));

    reportService
        .streamAdminCsvBatchedJoinRequest(request)
        .onSuccess(
            csv -> streamCsv(ctx, csv, "org_join_request_report.csv", "ORG_JOIN_REQUEST_REPORT"))
        .onFailure(ctx::fail);
  }

  public void getOrganizationReport(RoutingContext ctx) {

    LOGGER.info("Org list report initiated");

    PaginatedRequest request =
        buildRequest(
            ctx,
            ALLOWED_FILTER_MAP_FOR_ORG,
            API_TO_DB_ORG_USERS,
            Set.of(CREATED_AT),
            CREATED_AT,
            API_TO_DB_ORG_USERS.keySet(),
            null);

    reportService
        .streamAdminCsvBatchedOrganization(request)
        .onSuccess(csv -> streamCsv(ctx, csv, "org_report.csv", "ORG_LIST_REPORT"))
        .onFailure(ctx::fail);
  }

  public void getProviderRequestReport(RoutingContext ctx) {

    LOGGER.info("Provider role request report initiated");

    PaginatedRequest request =
        buildRequest(
            ctx,
            ALLOWED_FILTER_MAP_FOR_PROVIDER_ROLE_REQUEST,
            API_TO_DB_PROVIDER_ROLE_REQUEST,
            Set.of(CREATED_AT),
            CREATED_AT,
            API_TO_DB_PROVIDER_ROLE_REQUEST.keySet(),
            null);

    reportService
        .streamAdminCsvBatchedProviderRequest(request)
        .onSuccess(
            csv ->
                streamCsv(ctx, csv, "provider_request_report.csv", "PROVIDER_ROLE_REQUEST_REPORT"))
        .onFailure(ctx::fail);
  }

  public void getComputeRoleReport(RoutingContext ctx) {

    LOGGER.info("Compute role request report initiated");

    PaginatedRequest request =
        buildRequest(
            ctx,
            ALLOWED_FILTER_MAP_FOR_COMPUTE_ROLE,
            API_TO_DB_COMPUTE_ROLE_REQUEST,
            Set.of(CREATED_AT),
            CREATED_AT,
            API_TO_DB_COMPUTE_ROLE_REQUEST.keySet(),
            null);

    reportService
        .streamAdminCsvBatchedComputeRequest(request)
        .onSuccess(
            csv ->
                streamCsv(
                    ctx, csv, "compute_role_request_report.csv", "COMPUTE_ROLE_REQUEST_REPORT"))
        .onFailure(ctx::fail);
  }

  public void getCreditRequestReport(RoutingContext ctx) {

    LOGGER.info("Credit request report initiated");

    PaginatedRequest request =
        buildRequest(
            ctx,
            ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST,
            API_TO_DB_CREDIT_REQUEST,
            Set.of(REQUESTED_AT),
            REQUESTED_AT,
            API_TO_DB_CREDIT_REQUEST.keySet(),
            null);

    reportService
        .streamAdminCsvBatchedCredit(request)
        .onSuccess(csv -> streamCsv(ctx, csv, "credit_request_report.csv", "CREDIT_REQUEST_REPORT"))
        .onFailure(ctx::fail);
  }

  /* =========================
   * Internal helpers
   * ========================= */

  private PaginatedRequest buildRequest(
      RoutingContext ctx,
      Map<String, String> allowedFiltersDbMap,
      Map<String, String> apiToDbMap,
      Set<String> allowedTimeFields,
      String defaultTimeField,
      Set<String> allowedSortFields,
      Map<String, Object> additionalFilters) {

    PaginationRequestBuilder builder =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(allowedFiltersDbMap)
            .apiToDbMap(apiToDbMap)
            .allowedTimeFields(allowedTimeFields)
            .defaultTimeField(defaultTimeField)
            .defaultSort(defaultTimeField, DEFAULT_SORTING_ORDER)
            .allowedSortFields(allowedSortFields);

    if (additionalFilters != null && !additionalFilters.isEmpty()) {
      builder.additionalFilters(additionalFilters);
    }

    return builder.build();
  }

  private void streamCsv(
      RoutingContext ctx, ReadStream<Buffer> csvStream, String filename, String reportName) {

    HttpServerResponse response = ctx.response();

    prepareCsvResponse(response, filename);

    if (csvStream == null) {
      LOGGER.warn("CSV stream is null | report={}", reportName);
      response.end();
      return;
    }

    csvStream
        .exceptionHandler(
            err -> {
              LOGGER.error("CSV streaming failed | report={}", reportName, err);
              ctx.fail(err);
            })
        .handler(response::write)
        .endHandler(
            v -> {
              LOGGER.info("CSV streaming completed | report={}", reportName);
              response.end();
            });
  }

  private void prepareCsvResponse(HttpServerResponse response, String filename) {
    response
        .putHeader(HEADER_ACAO, "*")
        .putHeader(HEADER_ACAH, HEADER_ALLOW_HEADERS_VALUE)
        .putHeader(HEADER_ACAM, HEADER_ALLOW_METHODS_VALUE)
        .putHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_CSV)
        .putHeader(HEADER_CONTENT_DISPOSITION, String.format(CONTENT_DISPOSITION_FMT, filename))
        .setChunked(true);
  }
}
