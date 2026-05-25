package org.cdpg.dx.aaa.organization.factory;

import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.organization.controller.OrganizationController;
import org.cdpg.dx.aaa.provider.controller.ProviderController;
import org.cdpg.dx.aaa.provider.handler.ProviderRoleHandler;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAOFactory;
import org.cdpg.dx.aaa.organization.handler.*;
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

  /* =========================
   * todo: UserService to be removed later this is a temporary measure
   *  because currently user service requires organization service and virce versa so, need to break the cyclic dependency
   *  possibly with the help of orchestrator
   * ========================= */
  public static OrganizationController create(
      UserService userService,
      AuditingHandler auditingHandler,
      EmailComposer emailComposer,
      PostgresService pgService,
      ElasticsearchService esService,
      KeycloakUserService keycloakUserService,
      URNGenerator urnGenerator,
      WebClient webClient,
      Boolean kycRequired,
      String docIndex,
      String apdURL) {

    OrganizationDAOFactory organizationDAOFactory = new OrganizationDAOFactory(pgService);

    PolicyDao policyDao = new PolicyDaoImpl(pgService);

    ItemService itemService =
        new ItemServiceImpl(
            esService, keycloakUserService, pgService, policyDao, webClient, docIndex, apdURL);

    OrganizationService organizationService =
        new OrganizationServiceImpl(organizationDAOFactory, keycloakUserService, itemService);

    /* =========================
     * Handlers (share service)
     * ========================= */

    OrganizationCommandHandler commandHandler =
        new OrganizationCommandHandler(organizationService, urnGenerator);

    OrganizationQueryHandler queryHandler =
        new OrganizationQueryHandler(organizationService, urnGenerator);

    OrganizationCreateRequestHandler createRequestHandler =
        new OrganizationCreateRequestHandler(
            organizationService, keycloakUserService, emailComposer, urnGenerator);

    OrganizationJoinRequestHandler joinRequestHandler =
        new OrganizationJoinRequestHandler(
            organizationService,
            userService,
            keycloakUserService,
            emailComposer,
            urnGenerator);

    OrganizationUserHandler userHandler =
        new OrganizationUserHandler(organizationService, userService, urnGenerator);

    ProviderRoleHandler providerRoleHandler =
        new ProviderRoleHandler(
            organizationService, userService, emailComposer, urnGenerator);

    /* =========================
     * Controller (ONLY wiring)
     * ========================= */

    return new OrganizationController(
        commandHandler,
        queryHandler,
        createRequestHandler,
        joinRequestHandler,
        userHandler,
        auditingHandler,
        kycRequired);
  }

  public static ProviderController createProviderController(
      OrganizationService organizationService,
      UserService userService,
      EmailComposer emailComposer,
      URNGenerator urnGenerator,
      AuditingHandler auditingHandler,
      Boolean kycRequired) {

    ProviderRoleHandler providerRoleHandler =
        new ProviderRoleHandler(organizationService, userService, emailComposer, urnGenerator);

    return new ProviderController(providerRoleHandler, auditingHandler, kycRequired);
  }

  /* =========================
  todo: this method is to be removed later this is just to help with the migration
   * ========================= */
  public static OrganizationService createService(
      PostgresService pgService, KeycloakUserService keycloakUserService, ItemService itemService) {
    OrganizationDAOFactory organizationDAOFactory = new OrganizationDAOFactory(pgService);
    return new OrganizationServiceImpl(organizationDAOFactory, keycloakUserService, itemService);
  }
}