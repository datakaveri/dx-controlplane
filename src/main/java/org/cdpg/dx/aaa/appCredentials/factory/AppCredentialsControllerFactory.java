package org.cdpg.dx.aaa.appCredentials.factory;

import org.cdpg.dx.aaa.appCredentials.controller.AppCredentialsController;
import org.cdpg.dx.aaa.appCredentials.dao.AppCredentialsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.impl.AppCredentialsDAOImpl;
import org.cdpg.dx.aaa.appCredentials.handler.AppCredentialsHandler;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.appCredentials.service.impl.AppCredentialsServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AppCredentialsControllerFactory {

  private AppCredentialsControllerFactory() {}

  public static AppCredentialsController create(PostgresService postgresService, URNGenerator urnGenerator) {
    AppCredentialsDAO appCredentialsDAO = new AppCredentialsDAOImpl(postgresService);
    AppCredentialsService appCredentialsService = new AppCredentialsServiceImpl(appCredentialsDAO);
    AppCredentialsHandler appCredentialsHandler = new AppCredentialsHandler(appCredentialsService, urnGenerator);
    return new AppCredentialsController(appCredentialsHandler);
  }
}
