package org.cdpg.dx.aaa.delegation.factory;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.Controller.CreditController;
import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;
import org.cdpg.dx.aaa.credit.factory.CreditControllerFactory;
import org.cdpg.dx.aaa.credit.handler.CreditHandler;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.credit.service.CreditServiceImpl;
import org.cdpg.dx.aaa.delegation.controller.DelegationController;
import org.cdpg.dx.aaa.delegation.dao.DelegationDAOFactory;
import org.cdpg.dx.aaa.delegation.handler.DelegationHandler;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.delegation.service.DelegationServiceImpl;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class DelegationControllerFactory {

  private static final Logger LOGGER = LogManager.getLogger(DelegationControllerFactory.class);

  private DelegationControllerFactory() {}

  public static DelegationController create(DelegationService delegationService, EmailComposer emailCompose, UserService userService, URNGenerator urnGenerator,KeycloakUserService keycloakUserService) {


    DelegationHandler delegationHandler = new DelegationHandler(delegationService,emailCompose,userService, urnGenerator,keycloakUserService);

    return new DelegationController(delegationHandler);
  }

  public static DelegationService createService(PostgresService pgService, KeycloakUserService keycloakUserService, OrganizationService organizationService) {
    DelegationDAOFactory delegationDAOFactory = new DelegationDAOFactory(pgService);
    return new DelegationServiceImpl(delegationDAOFactory,keycloakUserService,organizationService);

  }
}
