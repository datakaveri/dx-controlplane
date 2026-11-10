package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;

public class OrganizationHierarchyHandler {

  private static final Logger LOGGER = LogManager.getLogger(OrganizationHierarchyHandler.class);

  private final OrganizationService organizationService;
  private final URNGenerator urnGenerator;

  public OrganizationHierarchyHandler(
      OrganizationService organizationService, URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.urnGenerator = urnGenerator;
  }

  public void getHierarchyNodes(RoutingContext ctx) {

    UUID organizationId;
    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID"));
      return;
    }

    organizationService
        .getHierarchyNodes(organizationId)
        .onSuccess(
            nodes -> {
              List<JsonObject> result =
                  nodes.stream()
                      .map(OrganizationHierarchyNode::toJson)
                      .collect(Collectors.toList());

              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, new JsonObject(), OrganisationAuditOperation.GET);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  /**
   * Dept Admin: the department the caller administers plus all of its sub-departments
   * (ordered by path, so parents precede children). Only reachable by a dept_admin.
   */
  public void getDepartmentHierarchy(RoutingContext ctx) {

    UUID organizationId;
    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID"));
      return;
    }

    DeptAdminScope deptAdminScope = DeptAdminAccessHandler.scopeOf(ctx);
    if (deptAdminScope == null || !deptAdminScope.organizationId().equals(organizationId)) {
      ctx.fail(
          new DxForbiddenException("The org id of the user and the path parameter are not same"));
      return;
    }

    UUID adminOfNodeId = deptAdminScope.adminOfNodeId();

    organizationService
        .getHierarchyNode(organizationId, adminOfNodeId)
        .compose(
            root ->
                organizationService
                    .getHierarchyNodeDescendants(organizationId, adminOfNodeId)
                    .map(
                        descendants -> {
                          List<OrganizationHierarchyNode> nodes = new ArrayList<>();
                          nodes.add(root);
                          nodes.addAll(descendants);
                          return nodes;
                        }))
        .onSuccess(
            nodes -> {
              List<JsonObject> result =
                  nodes.stream()
                      .map(OrganizationHierarchyNode::toJson)
                      .collect(Collectors.toList());

              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, new JsonObject(), OrganisationAuditOperation.GET_HIERARCHY_NODES);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getHierarchyNode(RoutingContext ctx) {

    UUID organizationId;
    UUID nodeId;

    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
      nodeId = UUID.fromString(ctx.pathParam("department_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID or department ID"));
      return;
    }

    organizationService
        .getHierarchyNode(organizationId, nodeId)
        .onSuccess(
            node -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, node.toJson(), OrganisationAuditOperation.GET);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, node, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getHierarchyNodeChildren(RoutingContext ctx) {

    UUID organizationId;
    UUID nodeId;

    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
      nodeId = UUID.fromString(ctx.pathParam("department_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID or department ID"));
      return;
    }

    organizationService
        .getHierarchyNodeChildren(organizationId, nodeId)
        .onSuccess(
            nodes -> {
              List<JsonObject> result =
                  nodes.stream()
                      .map(OrganizationHierarchyNode::toJson)
                      .collect(Collectors.toList());

              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, new JsonObject(), OrganisationAuditOperation.GET);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getHierarchyNodeDescendants(RoutingContext ctx) {

    UUID organizationId;
    UUID nodeId;

    try {
      organizationId = UUID.fromString(ctx.pathParam("organization_id"));
      nodeId = UUID.fromString(ctx.pathParam("department_id"));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid organization ID or department ID"));
      return;
    }

    organizationService
        .getHierarchyNodeDescendants(organizationId, nodeId)
        .onSuccess(
            nodes -> {
              List<JsonObject> result =
                  nodes.stream()
                      .map(OrganizationHierarchyNode::toJson)
                      .collect(Collectors.toList());

              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, new JsonObject(), OrganisationAuditOperation.GET);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(ctx::fail);
  }
}
