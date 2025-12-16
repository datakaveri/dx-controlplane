package org.cdpg.dx.aaa.organization.factory;

import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.organization.controller.OrganizationController;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAOFactory;
import org.cdpg.dx.aaa.organization.handler.OrganizationHandler;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.organization.service.OrganizationServiceImpl;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class OrganizationControllerFactory {

  private static final Logger LOGGER = LogManager.getLogger(OrganizationControllerFactory.class);

  private OrganizationControllerFactory() {}

  public static OrganizationController create(
      UserService userService,
      AuditingHandler auditingHandler,
      EmailComposer emailComposer,
      PostgresService pgService,
      ElasticsearchService esService,
      CreditService creditService,
      KeycloakUserService keycloakUserService,
      URNGenerator urnGenerator,
      DelegationService delegationService,
      WebClient webClient,
      Boolean kycRequired,
      String docIndex,
      String apdURL) {

    OrganizationDAOFactory organizationDAOFactory = new OrganizationDAOFactory(pgService);
    PolicyDao policyDao = new PolicyDaoImpl(pgService);
    ItemService itemService =
        new ItemServiceImpl(esService, keycloakUserService, policyDao, webClient, docIndex, apdURL);
    OrganizationService organizationService =
        new OrganizationServiceImpl(organizationDAOFactory, keycloakUserService, itemService);

    OrganizationHandler organizationHandler =
        new OrganizationHandler(
            organizationService,
            userService,
            emailComposer,
            keycloakUserService,
            urnGenerator,
            delegationService);
    return new OrganizationController(organizationHandler, auditingHandler, kycRequired);
  }

  public static OrganizationService createService(
      PostgresService pgService, KeycloakUserService keycloakUserService, ItemService itemService) {

    OrganizationDAOFactory organizationDAOFactory = new OrganizationDAOFactory(pgService);
    return new OrganizationServiceImpl(organizationDAOFactory, keycloakUserService, itemService);
  }
}
