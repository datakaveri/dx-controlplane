package org.cdpg.dx.aaa.organization.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.*;
import static org.cdpg.dx.aaa.organization.config.Constants.*;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.handler.OrganizationReportHandler;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.ScopeRule;
import org.cdpg.dx.auth.model.Scopes;

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

  private final OrganizationReportHandler organizationReportHandler;

  public OrganizationReportController(OrganizationReportHandler organizationReportHandler) {
    this.organizationReportHandler = organizationReportHandler;
  }

  /* =========================
   * Route Registration
   * ========================= */

  @Override
  public void register(RouterBuilder builder) {

    Handler<RoutingContext> cosAdminAccess =
        AuthorizationHandler.forScopes(Scopes.ROLE_MANAGEMENT, Scopes.USER_MANAGEMENT,
            Scopes.ORG_MANAGEMENT);
    // Org join / provider role request reports back the org admin's "User Requests" panel, so
    // they accept the same scope as the list APIs they export (org admin or a delegate holding
    // org-user-management), restricted to the caller's own organisation in the handler.
    Handler<RoutingContext> orgUserRequestReportAccess =
        AuthorizationHandler.forScopes(Scopes.ROLE_MANAGEMENT, Scopes.ORG_MANAGEMENT,
            Scopes.ORG_USER_MANAGEMENT);
    // Platform-wide request reports require the same scope as the list APIs they export.
    Handler<RoutingContext> orgManagementAccess = AuthorizationHandler.forScopes(Scopes.ORG_MANAGEMENT);
    Handler<RoutingContext> userManagementAccess =
        AuthorizationHandler.forScopes(Scopes.USER_MANAGEMENT);

    builder
        .operation(OP_ORG_CREATE_REQUEST_REPORT)
        .handler(orgManagementAccess)
        .handler(organizationReportHandler::getOrganizationCreateReport);

    builder
        .operation(OP_ORG_LIST_REPORT)
        .handler(cosAdminAccess)
        .handler(organizationReportHandler::getOrganizationReport);

    builder
        .operation(OP_ORG_JOIN_REQUEST_REPORT)
        .handler(orgUserRequestReportAccess)
        .handler(organizationReportHandler::getOrganizationJoinReport);

    builder
        .operation(OP_COMPUTE_ROLE_REQUEST_REPORT)
        .handler(userManagementAccess)
        .handler(organizationReportHandler::getComputeRoleReport);

    builder
        .operation(OP_PROVIDER_ROLE_REQUEST_REPORT)
        .handler(orgUserRequestReportAccess)
        .handler(organizationReportHandler::getProviderRequestReport);

    builder
        .operation(OP_CREDIT_REQUEST_REPORT)
        .handler(userManagementAccess)
        .handler(organizationReportHandler::getCreditRequestReport);
  }
}
