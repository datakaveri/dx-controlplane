package org.cdpg.dx.aaa.user.factory;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.Controller.CreditController;
import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;

import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.credit.service.CreditServiceImpl;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.controller.UserController;
import org.cdpg.dx.aaa.user.dao.CustomRoleDAO;
import org.cdpg.dx.aaa.user.handler.UserHandler;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.aaa.user.service.UserServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.security.Key;

public class UserControllerFactory {
  private static final Logger LOGGER = LogManager.getLogger(UserControllerFactory.class);

  private UserControllerFactory() {}

  public static UserController create(UserService userService, URNGenerator urnGenerator) {


    UserHandler userHandler = new UserHandler(userService, urnGenerator);

    return new UserController(userHandler);
  }

  public static UserService createService(KeycloakUserService keycloakUserService, OrganizationService organizationService, CreditService creditService , ElasticsearchService elasticsearchService, CustomRoleDAO customRoleDAO,String docUserIndex) {
    return new UserServiceImpl(keycloakUserService,
      organizationService,
      creditService,
      elasticsearchService,
      customRoleDAO,
      docUserIndex);

  }


}
