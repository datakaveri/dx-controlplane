package org.cdpg.dx.aaa.organization.handler;

import static org.cdpg.dx.aaa.organization.config.Constants.CREATED_AT;
import static org.cdpg.dx.aaa.organization.config.Constants.ORGANIZATION_ID;
import static org.cdpg.dx.aaa.organization.config.Constants.STATUS;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyDefinitionRequest;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.organization.util.DepartmentPaths;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;

/**
 * Requests to add departments to an organization -- approved/rejected by cos_admin as one atomic
 * unit. The org_admin submits departments (each with nested sub-departments, without ids) to go
 * under an existing department ({@code parentDepartmentId}) or at the top level; a flat
 * organization's first request defines its departments. The server generates ids when the
 * departments are applied.
 */
public class OrganizationHierarchyDefinitionHandler {
  private static final Logger LOGGER =
      LogManager.getLogger(OrganizationHierarchyDefinitionHandler.class);

  static final String PARENT_DEPARTMENT_ID = "parentDepartmentId";
  static final String PARENT_DEPARTMENT = "parentDepartment";

  private static final Map<String, String> COS_ADMIN_FILTERS =
      Map.of("status", STATUS, "organizationId", ORGANIZATION_ID);

  private final OrganizationService organizationService;
  private final URNGenerator urnGenerator;
  private final EmailComposer emailComposer;

  public OrganizationHierarchyDefinitionHandler(
      OrganizationService organizationService,
      URNGenerator urnGenerator,
      EmailComposer emailComposer) {
    this.organizationService = organizationService;
    this.urnGenerator = urnGenerator;
    this.emailComposer = emailComposer;
  }

  /** org_admin: propose departments for their own organisation. */
  public void createDefinitionRequest(RoutingContext ctx) {
    UUID organizationId = ownOrganizationId(ctx);
    if (organizationId == null) {
      return;
    }

    if (ctx.body() == null || ctx.body().isEmpty() || ctx.body().asJsonObject() == null) {
      ctx.fail(new DxBadRequestException("Request Body is required and must be valid JSON."));
      return;
    }
    JsonObject body = ctx.body().asJsonObject();

    JsonArray departmentsJson;
    try {
      departmentsJson = body.getJsonArray("departments");
    } catch (ClassCastException e) {
      ctx.fail(new DxBadRequestException("departments must be a JSON array"));
      return;
    }
    if (departmentsJson == null || departmentsJson.isEmpty()) {
      ctx.fail(new DxBadRequestException("departments is required and cannot be empty"));
      return;
    }

    List<ProposedDepartmentNode> departments;
    try {
      departments = ProposedDepartmentNode.listFromJson(departmentsJson);
    } catch (Exception e) {
      ctx.fail(new DxBadRequestException("Invalid departments: " + e.getMessage()));
      return;
    }

    UUID parentNodeId = null;
    String parent = body.getString(PARENT_DEPARTMENT_ID);
    if (parent != null && !parent.isBlank()) {
      try {
        parentNodeId = UUID.fromString(parent.trim());
      } catch (IllegalArgumentException e) {
        ctx.fail(new DxBadRequestException(PARENT_DEPARTMENT_ID + " must be a UUID"));
        return;
      }
    }

    organizationService
        .createHierarchyDefinitionRequest(organizationId, parentNodeId, departments)
        .onSuccess(
            created ->
                emailComposer
                    .sendEmailForDepartmentRequest(created, ctx.user())
                    .onFailure(err -> LOGGER.error("Department request email failed", err)))
        .compose(this::withParentDepartment)
        .onSuccess(
            request -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, request, OrganisationAuditOperation.REQUEST_HIERARCHY_DEFINITION);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, request, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  /** org_admin: their own organisation's requests. */
  public void getDefinitionRequests(RoutingContext ctx) {
    UUID organizationId = ownOrganizationId(ctx);
    if (organizationId == null) {
      return;
    }

    organizationService
        .getAllHierarchyDefinitionRequests(organizationId)
        .compose(this::withParentDepartments)
        .onSuccess(
            result -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, new JsonObject(), OrganisationAuditOperation.GET);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  /** org_admin: one of their own organisation's requests. */
  public void getDefinitionRequest(RoutingContext ctx) {
    UUID organizationId = ownOrganizationId(ctx);
    if (organizationId == null) {
      return;
    }

    UUID requestId;
    try {
      requestId = UUID.fromString(ctx.pathParam("request_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid request ID"));
      return;
    }

    organizationService
        .getHierarchyDefinitionRequestById(requestId)
        .compose(
            request ->
                request == null || !request.organizationId().equals(organizationId)
                    ? Future.<OrganizationHierarchyDefinitionRequest>failedFuture(
                        new DxNotFoundException("Hierarchy definition request not found"))
                    : Future.succeededFuture(request))
        .compose(this::withParentDepartment)
        .onSuccess(
            request -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, request, OrganisationAuditOperation.GET);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, request, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  /** cos_admin: the review queue across organisations, filterable by status and organisation. */
  public void getAllDefinitionRequests(RoutingContext ctx) {
    PaginatedRequest request;
    try {
      request =
          PaginationRequestBuilder.from(ctx)
              .allowedFiltersDbMap(COS_ADMIN_FILTERS)
              .apiToDbMap(Map.of("createdAt", CREATED_AT, "status", STATUS))
              .allowedTimeFields(Set.of(CREATED_AT))
              .defaultTimeField(CREATED_AT)
              .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
              .allowedSortFields(Set.of("createdAt", "status"))
              .build();
    } catch (RuntimeException e) {
      ctx.fail(e);
      return;
    }

    organizationService
        .getHierarchyDefinitionRequests(request)
        .compose(
            page ->
                withParentDepartments(page.data())
                    .map(result -> Map.entry(result, page.paginationInfo())))
        .onSuccess(
            page -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, new JsonObject(), OrganisationAuditOperation.GET);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, page.getKey(), page.getValue(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void updateDefinitionRequestStatus(RoutingContext ctx) {
    UUID requestId;
    try {
      requestId = UUID.fromString(ctx.pathParam("request_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid request ID"));
      return;
    }

    if (ctx.body() == null || ctx.body().isEmpty() || ctx.body().asJsonObject() == null) {
      ctx.fail(new DxBadRequestException("Request Body is required and must be valid JSON."));
      return;
    }

    JsonObject body = ctx.body().asJsonObject();
    Status status;
    try {
      status = Status.fromString(body.getString("status"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid status"));
      return;
    }

    String remarks = body.getString("remarks");

    organizationService
        .updateHierarchyDefinitionRequestStatus(requestId, status, remarks)
        .onSuccess(
            request -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx,
                      request.toJson(),
                      OrganisationAuditOperation.UPDATE_HIERARCHY_DEFINITION_REQUEST);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, request, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  /**
   * The {@code organization_id} path param, if it is the caller's own organisation. Otherwise
   * fails the request and returns null: an org_admin may only manage their own organisation.
   */
  private static UUID ownOrganizationId(RoutingContext ctx) {
    UUID organizationId;
    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID"));
      return null;
    }
    if (!organizationId.toString().equals(RoutingContextHelper.fromPrincipal(ctx).organisationId())) {
      ctx.fail(
          new DxForbiddenException("The org id of the user and the path parameter are not same"));
      return null;
    }
    return organizationId;
  }

  /**
   * A single request in the shape single-request responses have always had (the record's
   * camelCase fields, incl. {@code parentNodeId}), plus {@code parentDepartment}.
   */
  private Future<JsonObject> withParentDepartment(OrganizationHierarchyDefinitionRequest request) {
    return departmentPaths(List.of(request))
        .map(paths -> addParent(JsonObject.mapFrom(request), request, paths));
  }

  /**
   * Requests in the shape list responses have always had ({@code toJson}, snake_case, incl.
   * {@code parent_node_id}), each plus {@code parentDepartment} when it has a parent.
   */
  private Future<List<JsonObject>> withParentDepartments(
      List<OrganizationHierarchyDefinitionRequest> requests) {
    return departmentPaths(requests)
        .map(paths -> requests.stream().map(r -> toJson(r, paths)).toList());
  }

  private Future<DepartmentPaths> departmentPaths(
      List<OrganizationHierarchyDefinitionRequest> requests) {
    List<UUID> orgIds =
        requests.stream()
            .filter(request -> request.parentNodeId() != null)
            .map(OrganizationHierarchyDefinitionRequest::organizationId)
            .distinct()
            .toList();
    return orgIds.isEmpty()
            ? Future.succeededFuture(new DepartmentPaths(List.of()))
            : organizationService
                .getHierarchyNodesByOrganizationIds(orgIds)
                .map(
                    nodesByOrg ->
                        new DepartmentPaths(
                            nodesByOrg.values().stream().flatMap(List::stream).toList()));
  }

  static JsonObject toJson(
      OrganizationHierarchyDefinitionRequest request, DepartmentPaths departments) {
    return addParent(request.toJson(), request, departments);
  }

  private static JsonObject addParent(
      JsonObject json, OrganizationHierarchyDefinitionRequest request, DepartmentPaths paths) {
    JsonObject parent = paths.describe(request.parentNodeId());
    if (parent != null) {
      json.put(PARENT_DEPARTMENT, parent);
    }
    return json;
  }
}
