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
    private final Boolean kycRequired;

    public OrganizationController(OrganizationHandler organizationHandler, AuditingHandler auditingHandler,Boolean kycRequired) {
        this.organizationHandler = organizationHandler;
        this.auditingHandler = auditingHandler;
        this.kycRequired = kycRequired;
    }

    @Override
    public void register(RouterBuilder routerBuilder) {

        routerBuilder
                .operation("get-auth-v2-organisations-request")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
                .handler(organizationHandler::getOrganisationRequest);

         routerBuilder
              .operation("get-auth-v2-user-organisations-request")
              .handler(auditingHandler::handleApiAudit)
              .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
              .handler(organizationHandler::getUserOrganisationRequest);

          routerBuilder
              .operation("delete-auth-v2-user-organisations-request")
              .handler(auditingHandler::handleApiAudit)
              .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
              .handler(organizationHandler::deleteOrganizationCreateRequest);


      routerBuilder
                .operation("post-auth-v2-organisations-request")
                .handler(auditingHandler::handleApiAudit)
//                .handler(AuthorizationHandler.requireKycVerified())
                .handler(kycRequired ? AuthorizationHandler.requireKycVerified() : ctx -> ctx.next())
                .handler(organizationHandler::createOrganisationRequest);

        routerBuilder
                .operation("post-auth-v2-approve-create_org")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
                .handler(organizationHandler::approveOrganisationRequest);

        routerBuilder
                .operation("post-auth-v2-organisations-join-requests")
                .handler(auditingHandler::handleApiAudit)
                //.handler(AuthorizationHandler.requireKycVerified())
                .handler(kycRequired ? AuthorizationHandler.requireKycVerified() : ctx -> ctx.next())
                .handler(organizationHandler::joinOrganisationRequest);

        routerBuilder
                .operation("get-auth-v2-organisations-join-requests")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN,DxRole.DELEGATE))
                .handler(organizationHandler::getJoinOrganisationRequests);

         routerBuilder
                .operation("get-auth-v2-user-organisations-join-requests")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
                .handler(organizationHandler::getUserJoinOrganisationRequests);

         routerBuilder
              .operation("delete-auth-v2-user-organisations-join-requests")
              .handler(auditingHandler::handleApiAudit)
              .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
              .handler(organizationHandler::deleteUserJoinOrganisationRequests);


        routerBuilder
                .operation("put-auth-v2-organisations-join-requests")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN,DxRole.DELEGATE))
                .handler(organizationHandler::approveJoinOrganisationRequests);

        routerBuilder
                .operation("get-auth-v2-org")
                .handler(auditingHandler::handleApiAudit)
                .handler(organizationHandler::listAllOrganisations);

        routerBuilder
                .operation("delete-auth-v2-organisations-id")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN, DxRole.ORG_ADMIN,DxRole.DELEGATE))
                .handler(organizationHandler::deleteOrganisationById);

        routerBuilder
                .operation("put-auth-v2-organisations-id")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN,DxRole.DELEGATE))
                .handler(organizationHandler::updateOrganisationById);

        //Organization User
        routerBuilder
                .operation("get-auth-v2-org-users")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN,DxRole.DELEGATE))
                .handler(organizationHandler::getOrganisationUsers);
        routerBuilder
                .operation("get-auth-v2-organisations-id-users-user_id")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN,DxRole.DELEGATE))
                .handler(organizationHandler::getOrganisationUserInfo);

        routerBuilder
                .operation("delete-auth-v2-organisations-users-id")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::deleteOrganisationUserById);

        routerBuilder
                .operation("put-auth-v2-organization-users-role")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::updateOrganisationUserRole);

        routerBuilder
                .operation("post-auth-v2-user-roles")
                .handler(auditingHandler::handleApiAudit)
                .handler(organizationHandler::createProviderRequest);

        routerBuilder
                .operation("get-auth-v2-user-roles")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN,DxRole.DELEGATE))
                .handler(organizationHandler::getProviderRequest);

        routerBuilder
                .operation("put-auth-v2-user-roles")
                .handler(auditingHandler::handleApiAudit)
                .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
                .handler(organizationHandler::updateProviderRequest);


      routerBuilder
        .operation("get-auth-v2-user-provider-requests")
        .handler(auditingHandler::handleApiAudit)
        .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
        .handler(organizationHandler::getProviderRoleRequest);

      routerBuilder
        .operation("delete-auth-v2-user-provider-requests")
        .handler(auditingHandler::handleApiAudit)
        .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
        .handler(organizationHandler::deleteUserProviderRoleRequest);


      routerBuilder
        .operation("post-auth-v2-organization-user-provider")
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN))
        .handler(organizationHandler::createProviderRole);

        routerBuilder
                .operation("get-auth-v2-organisations-id")
                .handler(auditingHandler::handleApiAudit)
                .handler(organizationHandler::getOrganizationById);

        routerBuilder
                .operation("get-auth-v2-organisations-requests-report")
                .handler(organizationHandler::getOrganizationCreateReport);

        routerBuilder
                .operation("get-auth-v2-organisations-report")
                .handler(organizationHandler::getOrganizationReport);

        routerBuilder
                .operation("get-auth-v2-organisations-join_requests-report")
                .handler(organizationHandler::getOrganizationJoinReport);

        routerBuilder
                .operation("get-auth-v2-compute-requests-report")
                .handler(organizationHandler::getComputeRoleReport);

        routerBuilder
                .operation("get-auth-v2-organization-user-provider_role-requests-report")
                .handler(organizationHandler::getProviderRequestReport);

        routerBuilder
                .operation("get-auth-v2-credit-request-report")
                .handler(organizationHandler::getCreditRequestReport);




    }


}
