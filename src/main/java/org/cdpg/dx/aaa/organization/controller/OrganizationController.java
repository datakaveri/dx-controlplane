package org.cdpg.dx.aaa.organization.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.*;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.organization.handler.*;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;

public class OrganizationController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(OrganizationController.class);

  private final OrganizationCommandHandler commandHandler;
  private final OrganizationQueryHandler queryHandler;
  private final OrganizationCreateRequestHandler createRequestHandler;
  private final OrganizationJoinRequestHandler joinRequestHandler;
  private final OrganizationUserHandler userHandler;
  private final ProviderRoleHandler providerRoleHandler;
  private final AuditingHandler auditingHandler;
  private final Boolean isKycRequired;

  public OrganizationController(
      OrganizationCommandHandler commandHandler,
      OrganizationQueryHandler queryHandler,
      OrganizationCreateRequestHandler createRequestHandler,
      OrganizationJoinRequestHandler joinRequestHandler,
      OrganizationUserHandler userHandler,
      ProviderRoleHandler providerRoleHandler,
      AuditingHandler auditingHandler,
      Boolean isKycRequired) {

    this.commandHandler = commandHandler;
    this.queryHandler = queryHandler;
    this.createRequestHandler = createRequestHandler;
    this.joinRequestHandler = joinRequestHandler;
    this.userHandler = userHandler;
    this.providerRoleHandler = providerRoleHandler;
    this.auditingHandler = auditingHandler;
    this.isKycRequired = isKycRequired;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    /* =========================
     * Organization create request
     * ========================= */

    routerBuilder
        .operation(OP_GET_ORG_CREATE_REQUESTS)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN, DxRole.DELEGATE))
        .handler(createRequestHandler::getAllOrganisationRequest);

    //todo: delegatorId
    //done
    routerBuilder
        .operation(OP_GET_USER_ORG_CREATE_REQUESTS)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER, DxRole.DELEGATE))
        .handler(createRequestHandler::getUserOrganisationRequest);

    //todo: delegatorId
    //done
    routerBuilder
        .operation(OP_DELETE_USER_ORG_CREATE_REQUEST)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER,DxRole.DELEGATE))
        .handler(createRequestHandler::deleteOrganizationCreateRequest);

    //todo: refactor code
    routerBuilder
        .operation(OP_CREATE_ORG_REQUEST)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.KycVerification(isKycRequired))
        .handler(createRequestHandler::createOrganisationRequest);

    routerBuilder
        .operation(OP_APPROVE_ORG_CREATE_REQUEST)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN, DxRole.DELEGATE))
        .handler(createRequestHandler::updateOrganisationRequest);

    /* =========================
     * Organization join requests
     * ========================= */

    //TODO : DELEGATOR id
    //done
    routerBuilder
        .operation(OP_CREATE_ORG_JOIN_REQUEST)
        .handler(auditingHandler::handleApiAudit) // Done
        .handler(AuthorizationHandler.KycVerification(isKycRequired))
        .handler(joinRequestHandler::joinOrganisationRequest);

    routerBuilder
        .operation(OP_GET_ORG_JOIN_REQUESTS)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN, DxRole.DELEGATE))
        .handler(joinRequestHandler::getJoinOrganisationRequests);

    // todo : delegatorId
    //done
    routerBuilder
        .operation(OP_GET_USER_ORG_JOIN_REQUESTS)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER,DxRole.DELEGATE))
        .handler(joinRequestHandler::getUserJoinOrganisationRequests);

    routerBuilder
      .operation(OP_WITHDRAW_USER_ORG_JOIN_REQUESTS)
      .handler(auditingHandler::handleApiAudit) // done
      .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER,DxRole.DELEGATE))
      .handler(joinRequestHandler::withdrawJoinRequest);

    // todo : delegatorId
    //done
    routerBuilder
        .operation(OP_DELETE_USER_ORG_JOIN_REQUEST)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER,DxRole.DELEGATE))
        .handler(joinRequestHandler::deleteUserJoinOrganisationRequests);

    routerBuilder
        .operation(OP_APPROVE_ORG_JOIN_REQUEST)
        .handler(auditingHandler::handleApiAudit) // Done
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN, DxRole.DELEGATE))
        .handler(joinRequestHandler::approveJoinOrganisationRequests);

    /* =========================
     * Organization core
     * ========================= */

    routerBuilder
        .operation(OP_LIST_ORGANISATIONS)
        .handler(auditingHandler::handleApiAudit) // not required
        .handler(queryHandler::listAllOrganisations);

    routerBuilder
        .operation(OP_GET_ORGANISATION_BY_ID)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(queryHandler::getOrganizationById);

    routerBuilder
        .operation(OP_UPDATE_ORGANISATION_BY_ID)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN, DxRole.DELEGATE))
        .handler(commandHandler::updateOrganisationById);

    //TODO: check orgId and userId same
    //done - delegator
    routerBuilder
        .operation(OP_DELETE_ORGANISATION_BY_ID)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.COS_ADMIN, DxRole.ORG_ADMIN, DxRole.DELEGATE))
        .handler(commandHandler::deleteOrganisationById);

    /* =========================
     * Organization users
     * ========================= */

    routerBuilder
        .operation(OP_GET_ORG_USERS)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN, DxRole.DELEGATE))
        .handler(userHandler::getOrganisationUsers);

    routerBuilder
        .operation(OP_GET_ORG_USER_INFO)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN, DxRole.DELEGATE))
        .handler(userHandler::getOrganisationUserInfo);

    //todo : delegator id
    //done
    routerBuilder
        .operation(OP_DELETE_ORG_USER)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN , DxRole.DELEGATE))
        .handler(userHandler::deleteOrganisationUserById);

    routerBuilder
        .operation(OP_UPDATE_ORG_USER_ROLE)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN , DxRole.DELEGATE))
        .handler(userHandler::updateOrganisationUserRole);

    /* =========================
     * Provider roles
     * ========================= */

    routerBuilder
        .operation(OP_CREATE_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(providerRoleHandler::createProviderRequest);

    routerBuilder
        .operation(OP_GET_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN, DxRole.DELEGATE))
        .handler(providerRoleHandler::getProviderRequest);

    routerBuilder
        .operation(OP_UPDATE_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN, DxRole.DELEGATE))
        .handler(providerRoleHandler::updateProviderRequest);

    // todo: delegatorId
    //done
    routerBuilder
        .operation(OP_GET_USER_PROVIDER_REQUESTS)
        .handler(auditingHandler::handleApiAudit)
        .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER, DxRole.DELEGATE))
        .handler(providerRoleHandler::getProviderRoleRequest);

    //todo:delegatorId
    //done
    routerBuilder
        .operation(OP_DELETE_USER_PROVIDER_REQUEST)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER, DxRole.DELEGATE))
        .handler(providerRoleHandler::deleteUserProviderRoleRequest);

    //todo : scopes
    //done
    routerBuilder
        .operation(OP_CREATE_PROVIDER_ROLE)
        .handler(auditingHandler::handleApiAudit) // done
        .handler(AuthorizationHandler.forRoles(DxRole.ORG_ADMIN,DxRole.DELEGATE))
        .handler(providerRoleHandler::createProviderRole);
  }
}
