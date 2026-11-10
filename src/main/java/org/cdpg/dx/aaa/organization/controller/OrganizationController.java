package org.cdpg.dx.aaa.organization.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.*;
import static org.cdpg.dx.auth.authorization.handler.AuthorizationHandler.kycVerification;

import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.organization.handler.*;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.ScopeRule;
import org.cdpg.dx.auth.model.Scopes;

public class OrganizationController implements ApiController {

  private static final Logger LOGGER = LogManager.getLogger(OrganizationController.class);

  private final OrganizationCommandHandler commandHandler;
  private final OrganizationQueryHandler queryHandler;
  private final OrganizationCreateRequestHandler createRequestHandler;
  private final OrganizationJoinRequestHandler joinRequestHandler;
  private final OrganizationHierarchyHandler hierarchyHandler;
  private final OrganizationHierarchyDefinitionHandler hierarchyDefinitionHandler;
  private final DeptAdminHandler deptAdminHandler;
  private final OrganizationUserHandler userHandler;
  private final DeptAdminAccessHandler deptAdminAccess;
  private final AuditingHandler auditingHandler;
  private final Boolean isKycRequired;

  public OrganizationController(
      OrganizationCommandHandler commandHandler,
      OrganizationQueryHandler queryHandler,
      OrganizationCreateRequestHandler createRequestHandler,
      OrganizationJoinRequestHandler joinRequestHandler,
      OrganizationHierarchyHandler hierarchyHandler,
      OrganizationHierarchyDefinitionHandler hierarchyDefinitionHandler,
      DeptAdminHandler deptAdminHandler,
      OrganizationUserHandler userHandler,
      DeptAdminAccessHandler deptAdminAccess,
      AuditingHandler auditingHandler,
      Boolean isKycRequired) {

    this.commandHandler = commandHandler;
    this.queryHandler = queryHandler;
    this.createRequestHandler = createRequestHandler;
    this.joinRequestHandler = joinRequestHandler;
    this.hierarchyHandler = hierarchyHandler;
    this.hierarchyDefinitionHandler = hierarchyDefinitionHandler;
    this.deptAdminHandler = deptAdminHandler;
    this.userHandler = userHandler;
    this.deptAdminAccess = deptAdminAccess;
    this.auditingHandler = auditingHandler;
    this.isKycRequired = isKycRequired;
  }

  @Override
  public void register(RouterBuilder routerBuilder) {

    var selfAccess = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);
    var orgAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_USER_MANAGEMENT);
    var cosAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_MANAGEMENT);
    var orgDeleteAccess =
        AuthorizationHandler.forScopesWithContext(
            ScopeRule.platform(Scopes.ORG_MANAGEMENT),
            ScopeRule.org(Scopes.ORG_USER_MANAGEMENT));
    // org_admin, or a dept_admin -- whose actions the handlers/services then
    // narrow to their own department subtree (see DeptAdminAccessHandler).
    var orgOrDeptAdminAccess =
        deptAdminAccess.withDeptScopes(
            AuthorizationHandler.forScopes(
                Scopes.ORG_USER_MANAGEMENT, Scopes.DEPT_USER_MANAGEMENT));
    var deptAdminOnlyAccess =
        deptAdminAccess.withDeptScopes(
            AuthorizationHandler.forScopes(Scopes.DEPT_USER_MANAGEMENT));

    /* =========================
     * Organization create request
     * ========================= */

    routerBuilder
        .operation(OP_GET_ORG_CREATE_REQUESTS)
        .handler(auditingHandler::handleApiAudit)
        .handler(cosAdminAccess)
        .handler(createRequestHandler::getAllOrganisationRequest);

    routerBuilder
        .operation(OP_GET_USER_ORG_CREATE_REQUESTS)
        .handler(auditingHandler::handleApiAudit)
        //.handler(selfAccess)
        .handler(createRequestHandler::getUserOrganisationRequest);

    routerBuilder
        .operation(OP_DELETE_USER_ORG_CREATE_REQUEST)
        .handler(auditingHandler::handleApiAudit)
      //  .handler(selfAccess)
        .handler(createRequestHandler::deleteOrganizationCreateRequest);

    routerBuilder
        .operation(OP_CREATE_ORG_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        //.handler(selfAccess)
        .handler(kycVerification(isKycRequired))
        .handler(createRequestHandler::createOrganisationRequest);

    routerBuilder
        .operation(OP_APPROVE_ORG_CREATE_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(cosAdminAccess)
        .handler(createRequestHandler::updateOrganisationRequest);

    /* =========================
     * Organization join requests
     * ========================= */

    routerBuilder
        .operation(OP_CREATE_ORG_JOIN_REQUEST)
        .handler(auditingHandler::handleApiAudit)
       // .handler(selfAccess)
        .handler(kycVerification(isKycRequired))
        .handler(joinRequestHandler::joinOrganisationRequest);

    routerBuilder
        .operation(OP_GET_ORG_JOIN_REQUESTS)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(joinRequestHandler::getJoinOrganisationRequests);

    routerBuilder
        .operation(OP_GET_DEPT_JOIN_REQUESTS)
        .handler(auditingHandler::handleApiAudit)
        .handler(deptAdminOnlyAccess)
        .handler(joinRequestHandler::getDepartmentJoinRequests);

    routerBuilder
        .operation(OP_GET_USER_ORG_JOIN_REQUESTS)
        .handler(auditingHandler::handleApiAudit)
        //.handler(selfAccess)
        .handler(joinRequestHandler::getUserJoinOrganisationRequests);

    routerBuilder
        .operation(OP_WITHDRAW_USER_ORG_JOIN_REQUESTS)
        .handler(auditingHandler::handleApiAudit)
       // .handler(selfAccess)
        .handler(joinRequestHandler::withdrawJoinRequest);

    routerBuilder
        .operation(OP_DELETE_USER_ORG_JOIN_REQUEST)
        .handler(auditingHandler::handleApiAudit)
       // .handler(selfAccess)
        .handler(joinRequestHandler::deleteUserJoinOrganisationRequests);

    routerBuilder
        .operation(OP_APPROVE_ORG_JOIN_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgOrDeptAdminAccess)
        .handler(joinRequestHandler::approveJoinOrganisationRequests);

    /* =========================
     * Organization core
     * ========================= */

    routerBuilder
        .operation(OP_LIST_ORGANISATIONS)
        .handler(auditingHandler::handleApiAudit)
       // .handler(selfAccess)
        .handler(queryHandler::listAllOrganisations);

    routerBuilder
        .operation(OP_GET_ORGANISATION_BY_ID)
        .handler(auditingHandler::handleApiAudit)
       // .handler(selfAccess)
        .handler(queryHandler::getOrganizationById);

    routerBuilder
        .operation(OP_UPDATE_ORGANISATION_BY_ID)
        .handler(auditingHandler::handleApiAudit)
        .handler(cosAdminAccess)
        .handler(commandHandler::updateOrganisationById);

    routerBuilder
        .operation(OP_DELETE_ORGANISATION_BY_ID)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgDeleteAccess)
        .handler(commandHandler::deleteOrganisationById);

    /* =========================
     * Organization users
     * ========================= */

    routerBuilder
        .operation(OP_GET_ORG_USERS)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(userHandler::getOrganisationUsers);

    routerBuilder
        .operation(OP_GET_DEPT_USERS)
        .handler(auditingHandler::handleApiAudit)
        .handler(deptAdminOnlyAccess)
        .handler(userHandler::getDepartmentUsers);

    routerBuilder
        .operation(OP_GET_ORG_USER_INFO)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgOrDeptAdminAccess)
        .handler(userHandler::getOrganisationUserInfo);

    routerBuilder
        .operation(OP_DELETE_ORG_USER)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgOrDeptAdminAccess)
        .handler(userHandler::deleteOrganisationUserById);

    routerBuilder
        .operation(OP_UPDATE_ORG_USER_ROLE)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgOrDeptAdminAccess)
        .handler(userHandler::updateOrganisationUserRole);

    /* =========================
     * Organization hierarchy
     * ========================= */

    routerBuilder
        .operation(OP_GET_ORG_HIERARCHY_NODES)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(hierarchyHandler::getHierarchyNodes);

    routerBuilder
        .operation(OP_GET_DEPT_HIERARCHY)
        .handler(auditingHandler::handleApiAudit)
        .handler(deptAdminOnlyAccess)
        .handler(hierarchyHandler::getDepartmentHierarchy);

    routerBuilder
        .operation(OP_GET_ORG_HIERARCHY_NODE)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(hierarchyHandler::getHierarchyNode);

    routerBuilder
        .operation(OP_GET_ORG_HIERARCHY_NODE_CHILDREN)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(hierarchyHandler::getHierarchyNodeChildren);

    routerBuilder
        .operation(OP_GET_ORG_HIERARCHY_NODE_DESCENDANTS)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(hierarchyHandler::getHierarchyNodeDescendants);

    /* =========================
     * Organization hierarchy -- bulk definition request
     * (single submission, cos_admin approves/rejects as one unit)
     * ========================= */

    routerBuilder
        .operation(OP_CREATE_ORG_HIERARCHY_DEFINITION_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(hierarchyDefinitionHandler::createDefinitionRequest);

    routerBuilder
        .operation(OP_GET_ORG_HIERARCHY_DEFINITION_REQUESTS)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(hierarchyDefinitionHandler::getDefinitionRequests);

    routerBuilder
        .operation(OP_GET_ORG_HIERARCHY_DEFINITION_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccess)
        .handler(hierarchyDefinitionHandler::getDefinitionRequest);

    routerBuilder
        .operation(OP_APPROVE_ORG_HIERARCHY_DEFINITION_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(cosAdminAccess)
        .handler(hierarchyDefinitionHandler::updateDefinitionRequestStatus);

    /* =========================
     * Dept admin assignment / self-request
     * (authorization against the target department is done inside the
     * handler/service, not the route guard -- an org_admin or any ancestor
     * dept_admin may act, which plain scope membership can't express)
     * ========================= */

    routerBuilder
        .operation(OP_ASSIGN_DEPT_ADMIN)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgOrDeptAdminAccess)
        .handler(deptAdminHandler::assignDeptAdmin);

    routerBuilder
        .operation(OP_REQUEST_DEPT_ADMIN)
        .handler(auditingHandler::handleApiAudit)
        .handler(deptAdminHandler::requestDeptAdmin);

    routerBuilder
        .operation(OP_APPROVE_DEPT_ADMIN_REQUEST)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgOrDeptAdminAccess)
        .handler(deptAdminHandler::updateDeptAdminRequestStatus);

  }
}