package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;

import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;

import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_ORG;
import static org.cdpg.dx.aaa.organization.config.Constants.CREATED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class OrganizationQueryHandler {

  private final OrganizationService organizationService;
  private final URNGenerator urnGenerator;

  private static final String DEPARTMENTS_KEY = "departments";

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
        .compose(
            orgs ->
                organizationService
                    .getHierarchyNodesByOrganizationIds(
                        orgs.data().stream().map(Organization::id).toList())
                    .map(
                        departmentsByOrg -> {
                          List<JsonObject> result =
                              orgs.data().stream()
                                  .map(
                                      org ->
                                          org.toFilteredJson()
                                              .put(
                                                  DEPARTMENTS_KEY,
                                                  departmentsJson(departmentsByOrg, org.id())))
                                  .collect(Collectors.toList());
                          return Map.entry(result, orgs.paginationInfo());
                        }))
        .onSuccess(
            page -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                  ctx, new JsonObject(), OrganisationAuditOperation.GET_ORG);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, page.getKey(), page.getValue(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getOrganizationById(RoutingContext ctx) {
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    organizationService
        .getOrganizationById(orgId)
        .compose(
            org ->
                organizationService
                    .getHierarchyNodesByOrganizationIds(List.of(org.id()))
                    .map(
                        departmentsByOrg ->
                            org.toDetailJson()
                                .put(DEPARTMENTS_KEY, departmentsJson(departmentsByOrg, org.id()))))
        .onSuccess(
            org -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                  ctx, new JsonObject(), OrganisationAuditOperation.GET_ORG);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
              ResponseBuilder.sendSuccess(ctx, org, urnGenerator);
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

  /** The organization's departments as {id, name, path}, ordered by path; empty when flat. */
  private static JsonArray departmentsJson(
      Map<UUID, List<OrganizationHierarchyNode>> departmentsByOrg, UUID orgId) {
    JsonArray departments = new JsonArray();
    departmentsByOrg
        .getOrDefault(orgId, List.of())
        .forEach(node -> departments.add(node.toDepartmentSummaryJson()));
    return departments;
  }
}
