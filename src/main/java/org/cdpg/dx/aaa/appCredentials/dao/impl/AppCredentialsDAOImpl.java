package org.cdpg.dx.aaa.appCredentials.dao.impl;

import static org.cdpg.dx.aaa.appCredentials.util.Constants.*;

import org.cdpg.dx.aaa.appCredentials.dao.AppCredentialsDAO;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AppCredentialsDAOImpl extends AbstractBaseDAO<AppCredentials>
    implements AppCredentialsDAO {

  public AppCredentialsDAOImpl(PostgresService postgresService) {
    super(postgresService, "app_credentials", APP_ID, AppCredentials::fromJson);
  }
}
