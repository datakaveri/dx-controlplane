package org.cdpg.dx.aaa.organization.factory;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.controller.OrganizationController;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAOFactory;
import org.cdpg.dx.aaa.organization.handler.OrganizationHandler;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.organization.service.OrganizationServiceImpl;
import org.cdpg.dx.aaa.orgReport.service.OrganizationCreateReportService;
import org.cdpg.dx.aaa.orgReport.service.impl.OrganizationCreateRequestReportServiceImpl;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;


public class OrganizationControllerFactory {

  private static final Logger LOGGER = LogManager.getLogger(OrganizationControllerFactory.class);

  private OrganizationControllerFactory() {}

  public static OrganizationController create(OrganizationService organizationService, UserService userService, AuditingHandler auditingHandler, EmailComposer emailComposer, Vertx vertx, PostgresService pgService, CreditService creditService, KeycloakUserService keycloakUserService, URNGenerator urnGenerator, Boolean kycRequired, DelegationService delegationService) {

    OrganizationDAOFactory organizationDAOFactory = new OrganizationDAOFactory(pgService);
    CreditDAOFactory creditDAOFactory = new CreditDAOFactory(pgService);
    OrganizationCreateReportService organizationCreateReportService = new OrganizationCreateRequestReportServiceImpl(organizationDAOFactory, creditDAOFactory, vertx);
    OrganizationHandler  organizationHandler = new OrganizationHandler(organizationService, userService,emailComposer, organizationCreateReportService,creditService,keycloakUserService,urnGenerator,delegationService);
    return new OrganizationController(organizationHandler, auditingHandler,kycRequired);
  }

  public static OrganizationService createService(PostgresService pgService, KeycloakUserService keycloakUserService, ItemService itemService) {

    OrganizationDAOFactory organizationDAOFactory = new OrganizationDAOFactory(pgService);
    return new  OrganizationServiceImpl(organizationDAOFactory, keycloakUserService,itemService);

  }
}
