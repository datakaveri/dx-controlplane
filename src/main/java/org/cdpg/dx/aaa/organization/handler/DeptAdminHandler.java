package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;

/**
 * Assigning a dept_admin directly (by org_admin or an ancestor dept_admin),
 * and a member's self-request to become dept_admin of their own department
 * (approved by org_admin or a strict ancestor dept_admin).
 */
public class DeptAdminHandler {

  private final OrganizationService organizationService;
  private final URNGenerator urnGenerator;

  public DeptAdminHandler(OrganizationService organizationService, URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.urnGenerator = urnGenerator;
  }

  public void assignDeptAdmin(RoutingContext ctx) {
    UUID organizationId;
    UUID targetUserId;
    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
      targetUserId = UUID.fromString(ctx.pathParam("user_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID or user ID"));
      return;
    }

    if (ctx.body() == null || ctx.body().isEmpty() || ctx.body().asJsonObject() == null) {
      ctx.fail(new DxBadRequestException("Request Body is required and must be valid JSON."));
      return;
    }

    String departmentIdStr = ctx.body().asJsonObject().getString("departmentId");
    if (departmentIdStr == null || departmentIdStr.isBlank()) {
      ctx.fail(new DxBadRequestException("departmentId is required"));
      return;
    }

    UUID departmentId;
    try {
      departmentId = UUID.fromString(departmentIdStr);
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid departmentId"));
      return;
    }

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID assignerUserId = dxUser.sub();

    organizationService
        .assignDeptAdmin(organizationId, assignerUserId, targetUserId, departmentId)
        .onSuccess(
            updatedUser -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, updatedUser.toJson(), OrganisationAuditOperation.ASSIGN_DEPT_ADMIN);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, updatedUser, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void requestDeptAdmin(RoutingContext ctx) {
    UUID organizationId;
    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID"));
      return;
    }

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID requesterUserId = dxUser.sub();

    organizationService
        .requestDeptAdmin(organizationId, requesterUserId)
        .onSuccess(
            request -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, request.toJson(), OrganisationAuditOperation.REQUEST_DEPT_ADMIN);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, request, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void updateDeptAdminRequestStatus(RoutingContext ctx) {
    UUID organizationId;
    UUID requestId;
    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
      requestId = UUID.fromString(ctx.pathParam("request_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID or request ID"));
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

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID approverUserId = dxUser.sub();

    organizationService
        .updateDeptAdminRequestStatus(organizationId, requestId, approverUserId, status, remarks)
        .onSuccess(
            updatedUser -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx,
                      new JsonObject().put("id", requestId.toString()),
                      OrganisationAuditOperation.APPROVE_DEPT_ADMIN_REQUEST);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              String message =
                  status == Status.GRANTED
                      ? "Dept admin request approved successfully"
                      : "Dept admin request rejected successfully";
              ResponseBuilder.sendSuccess(ctx, message, urnGenerator);
            })
        .onFailure(ctx::fail);
  }
}
