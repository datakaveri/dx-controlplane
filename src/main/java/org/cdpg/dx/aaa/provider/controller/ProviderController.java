package org.cdpg.dx.aaa.provider.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.*;
import static org.cdpg.dx.auth.authorization.handler.AuthorizationHandler.kycVerification;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.provider.handler.ProviderRoleHandler;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.model.Scopes;

public class ProviderController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(ProviderController.class);

  private final ProviderRoleHandler providerRoleHandler;
  private final AuditingHandler auditingHandler;
  private final Boolean isKycRequired;

  public ProviderController(
      ProviderRoleHandler providerRoleHandler,
      AuditingHandler auditingHandler,
      Boolean isKycRequired) {
    this.providerRoleHandler = providerRoleHandler;
    this.auditingHandler = auditingHandler;
    this.isKycRequired = isKycRequired;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    var selfAccess = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);
    var orgAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_USER_MANAGEMENT);
    var cosAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_MANAGEMENT);

    /* =========================
     * Org-based provider requests (Consumer)
     * ========================= */

    routerBuilder
        .operation(OP_CREATE_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(selfAccess)
        .handler(kycVerification(isKycRequired))
        .handler(providerRoleHandler::createProviderRequest);

    routerBuilder
        .operation(OP_GET_USER_PROVIDER_REQUESTS)
        .handler(auditingHandler::handleApiAudit)
        .handler(selfAccess)
        .handler(providerRoleHandler::getProviderRoleRequest);

    routerBuilder
        .operation(OP_DELETE_USER_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(selfAccess)
        .handler(providerRoleHandler::deleteUserProviderRoleRequest);

    /* =========================
     * Org-based provider requests (Org Admin)
     * ========================= */

    routerBuilder
        .operation(OP_GET_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(providerRoleHandler::getProviderRequest);

    routerBuilder
        .operation(OP_UPDATE_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(providerRoleHandler::updateProviderRequest);

    routerBuilder
        .operation(OP_CREATE_PROVIDER_ROLE)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(providerRoleHandler::createProviderRole);

    /* =========================
     * Platform provider requests (Consumer)
     * ========================= */

    routerBuilder
        .operation(OP_CREATE_PLATFORM_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(selfAccess)
        .handler(kycVerification(isKycRequired))
        .handler(providerRoleHandler::createPlatformProviderRequest);

    routerBuilder
        .operation(OP_GET_USER_PLATFORM_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(selfAccess)
        .handler(providerRoleHandler::getUserPlatformProviderRequest);

    routerBuilder
        .operation(OP_DELETE_USER_PLATFORM_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(selfAccess)
        .handler(providerRoleHandler::deleteUserPlatformProviderRequest);

    /* =========================
     * Platform provider requests (COS Admin)
     * ========================= */

    routerBuilder
        .operation(OP_GET_PLATFORM_PROVIDER_REQUESTS)
        .handler(auditingHandler::handleApiAudit)
        .handler(cosAdminAccess)
        .handler(providerRoleHandler::getPlatformProviderRequests);

    routerBuilder
        .operation(OP_UPDATE_PLATFORM_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(cosAdminAccess)
        .handler(providerRoleHandler::updatePlatformProviderRequest);
  }
}
