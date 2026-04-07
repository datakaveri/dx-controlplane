package org.cdpg.dx.aaa.credit.factory;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.credit.Controller.CreditController;
import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;
import org.cdpg.dx.aaa.credit.handler.ComputeRoleHandler;
import org.cdpg.dx.aaa.credit.handler.CreditBalanceHandler;
import org.cdpg.dx.aaa.credit.handler.CreditRequestHandler;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.credit.service.CreditServiceImpl;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class CreditControllerFactory {
  private static final Logger LOGGER = LogManager.getLogger(CreditControllerFactory.class);

  private CreditControllerFactory() {}

  public static CreditController create(CreditService creditService, EmailComposer emailComposer, UserService userService, OrganizationService organizationService, KeycloakUserService keycloakUserService, AuditingHandler auditingHandler, URNGenerator urnGenerator, Boolean isKycRequired) {

    CreditRequestHandler creditRequestHandler = new CreditRequestHandler(creditService, emailComposer, keycloakUserService, urnGenerator);
    CreditBalanceHandler creditBalanceHandler = new CreditBalanceHandler(creditService, emailComposer, keycloakUserService, urnGenerator);
    ComputeRoleHandler computeRoleHandler = new ComputeRoleHandler(creditService, emailComposer, userService, organizationService, keycloakUserService, urnGenerator);

    return new CreditController(creditRequestHandler, creditBalanceHandler, computeRoleHandler, auditingHandler, isKycRequired);
  }

  public static CreditService createService(PostgresService pgService, KeycloakUserService keycloakUserService, JsonObject config) {
    CreditDAOFactory creditDAOFactory = new CreditDAOFactory(pgService);
    return new CreditServiceImpl(creditDAOFactory,keycloakUserService, config);

  }


}
