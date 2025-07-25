package org.cdpg.dx.aaa.credit.factory;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.credit.Controller.CreditController;
import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;
import org.cdpg.dx.aaa.credit.handler.CreditHandler;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.credit.service.CreditServiceImpl;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class CreditControllerFactory {
  private static final Logger LOGGER = LogManager.getLogger(CreditControllerFactory.class);

  private CreditControllerFactory() {}

  public static CreditController create(CreditService creditService, EmailComposer emailCompose, UserService userService) {


    CreditHandler creditHandler = new CreditHandler(creditService,emailCompose,userService);

    return new CreditController(creditHandler);
  }

  public static CreditService createService(PostgresService pgService, KeycloakUserService keycloakUserService, JsonObject config) {
    CreditDAOFactory creditDAOFactory = new CreditDAOFactory(pgService);
    return new CreditServiceImpl(creditDAOFactory,keycloakUserService, config);

  }


}
