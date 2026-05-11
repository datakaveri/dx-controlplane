package org.cdpg.dx.aaa.appCredentials.factory;

import org.cdpg.dx.aaa.appCredentials.controller.AppCredentialsController;
import org.cdpg.dx.aaa.appCredentials.dao.AppConstraintsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.AppCredentialsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.impl.AppConstraintsDAOImpl;
import org.cdpg.dx.aaa.appCredentials.dao.impl.AppCredentialsDAOImpl;
import org.cdpg.dx.aaa.appCredentials.handler.AppCredentialsHandler;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.appCredentials.service.impl.AppCredentialsServiceImpl;
import org.cdpg.dx.aaa.delegation.DelegationHandlerValidator;
import org.cdpg.dx.aaa.delegation.DelegationValidator;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auth.v2.factory.AuthHandlersV2;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;

public class AppCredentialsControllerFactory {

  private AppCredentialsControllerFactory() {}

  public static AppCredentialsController create(PostgresService postgresService, OrganizationService organizationService, ItemService itemService, URNGenerator urnGenerator, DataBrokerService dataBrokerService, String appIdRevokeExchange, UserService userService,  AuthHandlersV2 authV2) {
    AppCredentialsDAO appCredentialsDAO = new AppCredentialsDAOImpl(postgresService);
    AppConstraintsDAO appConstraintsDAO = new AppConstraintsDAOImpl(postgresService);
    DelegationHandlerValidator delegationHandlerValidator = new DelegationHandlerValidator();
    DelegationValidator delegationValidator = new DelegationValidator(organizationService, itemService);
    AppCredentialsService appCredentialsService = new AppCredentialsServiceImpl(delegationValidator, appCredentialsDAO, appConstraintsDAO, dataBrokerService, appIdRevokeExchange, userService);
    AppCredentialsHandler appCredentialsHandler = new AppCredentialsHandler(appCredentialsService, delegationHandlerValidator, urnGenerator);
    return new AppCredentialsController(appCredentialsHandler, authV2.authentication(), authV2.authorization());
  }
}
