package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.UpdateOrgDTO;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auth.authorization.model.AuthLevel;
import org.cdpg.dx.auth.authorization.model.AuthorizationContext;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RequestHelper;

import static org.cdpg.dx.aaa.common.Constants.ID;

public class OrganizationCommandHandler {
  private static final Logger LOGGER = LogManager.getLogger(OrganizationCommandHandler.class);

  private final OrganizationService organizationService;
  private final URNGenerator urnGenerator;

  public OrganizationCommandHandler(
      OrganizationService organizationService, URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.urnGenerator = urnGenerator;
  }

  public void updateOrganisationById(RoutingContext ctx) {
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UpdateOrgDTO updateOrgDTO = RequestHelper.parseBody(ctx, UpdateOrgDTO::fromJson);

    organizationService
        .updateOrganizationById(orgId, updateOrgDTO)
        .onSuccess(
            updatedOrg -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                  OrganizationAuditHelper.buildOrganisationAudit(
                      ctx, updatedOrg.toJson(), OrganisationAuditOperation.UPDATE_ORG);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
              ResponseBuilder.sendSuccess(ctx, updatedOrg, urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to Update Organization id: {}, message: {}",
                  orgId,
                  err.getMessage(),
                  err);
              ctx.fail(err);
            });
  }

  public void deleteOrganisationById(RoutingContext ctx) {
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    AuthorizationContext authCtx = ctx.get(AuthorizationContext.KEY);
    if (authCtx != null && authCtx.getLevel() == AuthLevel.ORG
        && !orgId.toString().equals(authCtx.getOrgId())) {
      ctx.fail(new DxForbiddenException("Cannot delete a different organisation"));
      return;
    }

    organizationService
        .deleteOrganization(orgId)
        .onSuccess(
            updatedOrg -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                  ctx, new JsonObject().put(ID, orgId.toString()), OrganisationAuditOperation.DELETE_ORG);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
              ResponseBuilder.sendSuccess(ctx, "Organisation deleted Successfully!", urnGenerator);
            })
        .onFailure(ctx::fail);
  }
}