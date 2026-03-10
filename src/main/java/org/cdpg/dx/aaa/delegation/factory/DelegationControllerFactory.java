package org.cdpg.dx.aaa.delegation.factory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.controller.DelegationController;
import org.cdpg.dx.aaa.delegation.dao.DelegationDAOFactory;
import org.cdpg.dx.aaa.delegation.handler.DelegationHandler;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.delegation.service.DelegationServiceImpl;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.common.util.resolver.DelegatorStrategyFactory;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class DelegationControllerFactory {

  private static final Logger LOGGER = LogManager.getLogger(DelegationControllerFactory.class);

  private DelegationControllerFactory() {}

  public static DelegationController create(DelegationService delegationService, EmailComposer emailCompose, UserService userService, DelegatorStrategyFactory delegatorStrategyFactory, URNGenerator urnGenerator, KeycloakUserService keycloakUserService) {


    DelegationHandler delegationHandler = new DelegationHandler(delegationService,emailCompose,userService,delegatorStrategyFactory, urnGenerator,keycloakUserService);

    return new DelegationController(delegationHandler);
  }

  public static DelegationService createService(PostgresService pgService, KeycloakUserService keycloakUserService, OrganizationService organizationService, ItemService itemService) {
    DelegationDAOFactory delegationDAOFactory = new DelegationDAOFactory(pgService);
    return new DelegationServiceImpl(delegationDAOFactory,keycloakUserService,organizationService,itemService);

  }
}
