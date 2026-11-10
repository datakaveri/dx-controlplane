package org.cdpg.dx.aaa.appCredentials.dao.impl;

import org.cdpg.dx.aaa.appCredentials.dao.AppConstraintsDAO;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AppConstraintsDAOImpl extends AbstractBaseDAO<AppConstraints>
  implements AppConstraintsDAO {

  public AppConstraintsDAOImpl(PostgresService postgresService) {
    super(postgresService, "app_constraints", "id", AppConstraints::fromJson);
  }
}
