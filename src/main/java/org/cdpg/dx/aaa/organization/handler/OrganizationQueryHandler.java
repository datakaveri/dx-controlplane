package org.cdpg.dx.aaa.organization.handler;

import io.vertx.ext.web.RoutingContext;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;

import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.util.RoutingContextHelper;

import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_ORG;
import static org.cdpg.dx.aaa.organization.config.Constants.CREATED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class OrganizationQueryHandler {

  private final OrganizationService organizationService;
  private final URNGenerator urnGenerator;

  public OrganizationQueryHandler(
      OrganizationService organizationService, URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.urnGenerator = urnGenerator;
  }

  public void listAllOrganisations(RoutingContext ctx) {
    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG)
            .apiToDbMap(API_TO_DB_ORG)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(API_TO_DB_ORG.keySet())
            .build();

    organizationService
        .getOrganizations(request)
        .onSuccess(
            orgs -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "GET",
                      "List All Organisations");
              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              ResponseBuilder.sendSuccess(
                  ctx,
                  orgs.data().stream()
                      .map(Organization::toFilteredJson)
                      .collect(Collectors.toList()),
                  orgs.paginationInfo(),
                  urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getOrganizationById(RoutingContext ctx) {
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    organizationService
        .getOrganizationById(orgId)
        .onSuccess(
            org -> {
              /* AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
                RoutingContextHelper.getRequestPath(ctx), "GET", "Get Organization By ID");
              RoutingContextHelper.setAuditingLog(ctx, auditLog);*/
              ResponseBuilder.sendSuccess(ctx, org.toJson(), urnGenerator);
            })
        .onFailure(
            err -> {
              if (err instanceof DxNotFoundException) {
                ctx.fail(new DxNotFoundException("Organization not found with id: " + orgId));
              } else {
                ctx.fail(err);
              }
            });
  }
}
