package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyDefinitionRequest;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;

/**
 * One-time "define the department hierarchy" request for an existing (flat)
 * organization -- approved/rejected by cos_admin as one atomic unit. The
 * org_admin submits the organization's top-level departments, each with
 * nested sub-departments, without any ids; the server generates them when the
 * tree is applied.
 */
public class OrganizationHierarchyDefinitionHandler {

  private final OrganizationService organizationService;
  private final URNGenerator urnGenerator;

  public OrganizationHierarchyDefinitionHandler(
      OrganizationService organizationService, URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.urnGenerator = urnGenerator;
  }

  public void createDefinitionRequest(RoutingContext ctx) {
    UUID organizationId;
    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID"));
      return;
    }

    if (ctx.body() == null || ctx.body().isEmpty() || ctx.body().asJsonObject() == null) {
      ctx.fail(new DxBadRequestException("Request Body is required and must be valid JSON."));
      return;
    }

    JsonArray departmentsJson;
    try {
      departmentsJson = ctx.body().asJsonObject().getJsonArray("departments");
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

    organizationService
        .createHierarchyDefinitionRequest(organizationId, departments)
        .onSuccess(
            request -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, request.toJson(), OrganisationAuditOperation.REQUEST_HIERARCHY_DEFINITION);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, request, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getDefinitionRequests(RoutingContext ctx) {
    UUID organizationId;
    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID"));
      return;
    }

    organizationService
        .getAllHierarchyDefinitionRequests(organizationId)
        .onSuccess(
            requests -> {
              List<JsonObject> result =
                  requests.stream()
                      .map(OrganizationHierarchyDefinitionRequest::toJson)
                      .collect(Collectors.toList());

              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, new JsonObject(), OrganisationAuditOperation.GET);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getDefinitionRequest(RoutingContext ctx) {
    UUID requestId;
    try {
      requestId = UUID.fromString(ctx.pathParam("request_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid request ID"));
      return;
    }

    organizationService
        .getHierarchyDefinitionRequestById(requestId)
        .onSuccess(
            request -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, request.toJson(), OrganisationAuditOperation.GET);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, request, urnGenerator);
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
}
