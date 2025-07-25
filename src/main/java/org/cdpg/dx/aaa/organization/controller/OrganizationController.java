package org.cdpg.dx.aaa.organization.controller;


import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.aaa.apiserver.ApiController;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.handler.OrganizationHandler;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;

public class OrganizationController implements ApiController {
    private static final Logger LOGGER = LogManager.getLogger(OrganizationController.class);
    private final OrganizationHandler organizationHandler;
    private final AuditingHandler auditingHandler;

    public OrganizationController(OrganizationHandler organizationHandler, AuditingHandler auditingHandler) {
        this.organizationHandler = organizationHandler;
        this.auditingHandler = auditingHandler;
    }

    @Override
    public void register(RouterBuilder routerBuilder) {

        routerBuilder
                .operation("get-auth-v1-organisations-request")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN))
                .handler(organizationHandler::getOrganisationRequest);

        routerBuilder
                .operation("post-auth-v1-organisations-request")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.requireKycVerified())
                .handler(organizationHandler::createOrganisationRequest);

        routerBuilder
                .operation("post-auth-v1-approve-create_org")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN))
                .handler(organizationHandler::approveOrganisationRequest);

        routerBuilder
                .operation("post-auth-v1-organisations-join-requests")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.requireKycVerified())
                .handler(organizationHandler::joinOrganisationRequest);

        routerBuilder
                .operation("get-auth-v1-organisations-join-requests")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::getJoinOrganisationRequests);

        routerBuilder
                .operation("put-auth-v1-organisations-join-requests")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::approveJoinOrganisationRequests);

        routerBuilder
                .operation("get-auth-v1-org")
                .handler(auditingHandler::handleApiAudit)
                .handler(organizationHandler::listAllOrganisations);

        routerBuilder
                .operation("delete-auth-v1-organisations-id")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN, DxRole.ORG_ADMIN))
                .handler(organizationHandler::deleteOrganisationById);

        routerBuilder
                .operation("put-auth-v1-organisations-id")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN))
                .handler(organizationHandler::updateOrganisationById);

        //Organization User
        routerBuilder
                .operation("get-auth-v1-org-users")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::getOrganisationUsers);
        routerBuilder
                .operation("get-auth-v1-organisations-id-users-user_id")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::getOrganisationUserInfo);

        routerBuilder
                .operation("delete-auth-v1-organisations-users-id")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::deleteOrganisationUserById);

        routerBuilder
                .operation("put-auth-v1-organization-users-role")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::updateOrganisationUserRole);

        routerBuilder
                .operation("post-auth-v1-user-roles")
                .handler(auditingHandler::handleApiAudit)
                .handler(organizationHandler::createProviderRequest);

        routerBuilder
                .operation("get-auth-v1-user-roles")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::getProviderRequest);

        routerBuilder
                .operation("put-auth-v1-user-roles")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::updateProviderRequest);

      routerBuilder
        .operation("post-auth-v1-organization-user-provider")
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
        .handler(organizationHandler::createProviderRole);

        routerBuilder
                .operation("get-auth-v1-organisations-id")
                .handler(auditingHandler::handleApiAudit)
                .handler(organizationHandler::getOrganizationById);

        routerBuilder
                .operation("get-auth-v1-organisations-requests-report")
                .handler(organizationHandler::getOrganizationCreateReport);

        routerBuilder
                .operation("get-auth-v1-organisations-report")
                .handler(organizationHandler::getOrganizationReport);

        routerBuilder
                .operation("get-auth-v1-organisations-join_requests-report")
                .handler(organizationHandler::getOrganizationJoinReport);

        routerBuilder
                .operation("get-auth-v1-compute-requests-report")
                .handler(organizationHandler::getComputeRoleReport);

        routerBuilder
                .operation("get-auth-v1-organization-user-provider_role-requests-report")
                .handler(organizationHandler::getProviderRequestReport);

        routerBuilder
                .operation("get-auth-v1-credit-request-report")
                .handler(organizationHandler::getCreditRequestReport);




    }


}
