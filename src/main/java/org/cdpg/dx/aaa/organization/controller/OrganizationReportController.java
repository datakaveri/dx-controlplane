package org.cdpg.dx.aaa.organization.controller;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.orgReport.service.OrganizationCreateReportService;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.util.RequestHelper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.cdpg.dx.aaa.apiserver.OperationIds.*;
import static org.cdpg.dx.aaa.credit.util.Constants.ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST;
import static org.cdpg.dx.aaa.credit.util.Constants.API_TO_DB_CREDIT_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class OrganizationReportController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(OrganizationReportController.class);

  /* =========================
   * HTTP Headers & Content
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

  /* =========================
   * CSV Filenames
   * ========================= */

  private static final String FILE_ORG_CREATE_REQUEST_REPORT = "org_create_request_report.csv";
  private static final String FILE_ORG_JOIN_REQUEST_REPORT = "org_join_request_report.csv";
  private static final String FILE_ORG_LIST_REPORT = "org_report.csv";
  private static final String FILE_PROVIDER_ROLE_REQUEST_REPORT = "provider_request_report.csv";
  private static final String FILE_COMPUTE_ROLE_REQUEST_REPORT = "compute_role_request_report.csv";
  private static final String FILE_CREDIT_REQUEST_REPORT = "credit_request_report.csv";

  /* =========================
   * Path Params
   * ========================= */

  private static final String PATH_PARAM_ORGANIZATION_ID = "id";

  /* =========================
   * Dependencies
   * ========================= */

  private final OrganizationCreateReportService organizationCreateReportService;

  // Will be used later for delegated access enforcement & audit
  private final DelegationService delegationService;

  public OrganizationReportController(
      OrganizationCreateReportService organizationCreateReportService,
      DelegationService delegationService) {
    this.organizationCreateReportService = organizationCreateReportService;
    this.delegationService = delegationService;
  }

  /* =========================
   * Route Registration
   * ========================= */

  @Override
  public void register(RouterBuilder builder) {

    builder.operation(OP_ORG_CREATE_REQUEST_REPORT).handler(this::getOrganizationCreateReport);

    builder.operation(OP_ORG_LIST_REPORT).handler(this::getOrganizationReport);

    builder.operation(OP_ORG_JOIN_REQUEST_REPORT).handler(this::getOrganizationJoinReport);

    builder.operation(OP_COMPUTE_ROLE_REQUEST_REPORT).handler(this::getComputeRoleReport);

    builder.operation(OP_PROVIDER_ROLE_REQUEST_REPORT).handler(this::getProviderRequestReport);

    builder.operation(OP_CREDIT_REQUEST_REPORT).handler(this::getCreditRequestReport);
  }

  /* =========================
   * Helpers
   * ========================= */

  private void prepareCsvResponse(HttpServerResponse response, String filename) {
    response
        .putHeader(HEADER_ACAO, "*")
        .putHeader(HEADER_ACAH, HEADER_ALLOW_HEADERS_VALUE)
        .putHeader(HEADER_ACAM, HEADER_ALLOW_METHODS_VALUE)
        .putHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_CSV)
        .putHeader(HEADER_CONTENT_DISPOSITION, String.format(CONTENT_DISPOSITION_FMT, filename))
        .setChunked(true);
  }

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
      ReadStream<Buffer> csvStream,
      RoutingContext ctx,
      HttpServerResponse response,
      String reportName) {

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

  /* =========================
   * Endpoint Handlers
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

    organizationCreateReportService
        .streamAdminCsvBatchedCreateRequest(request)
        .onSuccess(
            csvStream -> {
              prepareCsvResponse(ctx.response(), FILE_ORG_CREATE_REQUEST_REPORT);
              streamCsv(csvStream, ctx, ctx.response(), "ORG_CREATE_REQUEST_REPORT");
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to generate org create request report", err);
              ctx.fail(err);
            });
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

    organizationCreateReportService
        .streamAdminCsvBatchedJoinRequest(request)
        .onSuccess(
            csvStream -> {
              prepareCsvResponse(ctx.response(), FILE_ORG_JOIN_REQUEST_REPORT);
              streamCsv(csvStream, ctx, ctx.response(), "ORG_JOIN_REQUEST_REPORT");
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to generate org join request report | orgId={}", orgId, err);
              ctx.fail(err);
            });
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

    organizationCreateReportService
        .streamAdminCsvBatchedOrganization(request)
        .onSuccess(
            csvStream -> {
              prepareCsvResponse(ctx.response(), FILE_ORG_LIST_REPORT);
              streamCsv(csvStream, ctx, ctx.response(), "ORG_LIST_REPORT");
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to generate org list report", err);
              ctx.fail(err);
            });
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

    organizationCreateReportService
        .streamAdminCsvBatchedProviderRequest(request)
        .onSuccess(
            csvStream -> {
              prepareCsvResponse(ctx.response(), FILE_PROVIDER_ROLE_REQUEST_REPORT);
              streamCsv(csvStream, ctx, ctx.response(), "PROVIDER_ROLE_REQUEST_REPORT");
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to generate provider role request report", err);
              ctx.fail(err);
            });
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

    organizationCreateReportService
        .streamAdminCsvBatchedComputeRequest(request)
        .onSuccess(
            csvStream -> {
              prepareCsvResponse(ctx.response(), FILE_COMPUTE_ROLE_REQUEST_REPORT);
              streamCsv(csvStream, ctx, ctx.response(), "COMPUTE_ROLE_REQUEST_REPORT");
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to generate compute role request report", err);
              ctx.fail(err);
            });
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

    organizationCreateReportService
        .streamAdminCsvBatchedCredit(request)
        .onSuccess(
            csvStream -> {
              prepareCsvResponse(ctx.response(), FILE_CREDIT_REQUEST_REPORT);
              streamCsv(csvStream, ctx, ctx.response(), "CREDIT_REQUEST_REPORT");
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to generate credit request report", err);
              ctx.fail(err);
            });
  }
}
