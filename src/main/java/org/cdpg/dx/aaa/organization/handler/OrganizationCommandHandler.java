package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.UpdateOrgDTO;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auth.authentication.util.AccessValidator;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;

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

    // updates org
    // delegate requirement: scope - org_management and delegator is cos_admin
    // check if request param and delegated org id is same

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.COS_ADMIN.getRole()),
        List.of(DxScope.USER_MANAGEMENT.getScope(), DxScope.COS_ADMIN_ACCESS.getScope()));

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UpdateOrgDTO updateOrgDTO = RequestHelper.parseBody(ctx, UpdateOrgDTO::fromJson);

    organizationService
        .updateOrganizationById(orgId, updateOrgDTO)
        .onSuccess(
            updatedOrg -> {
              ActivityAuditLogBuilder auditLog =
                  OrganizationAuditHelper.buildOrganizationUpdateAudit(
                      ctx, updatedOrg.id(), updatedOrg.orgName(), updateOrgDTO.toJson());
              RoutingContextHelper.setAuditingLogNew(ctx, auditLog);
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
    organizationService
        .deleteOrganization(orgId)
        .onSuccess(
            updatedOrg -> {
              ActivityAuditLogBuilder auditLog =
                  OrganizationAuditHelper.buildOrganizationDeleteAudit(ctx, orgId, null);
              RoutingContextHelper.setAuditingLogNew(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, updatedOrg, urnGenerator);
              ResponseBuilder.sendSuccess(ctx, "Organisation deleted Successfully!", urnGenerator);
            })
        .onFailure(ctx::fail);
  }
}
