package org.cdpg.dx.aaa.user.dao.impl;

import static org.cdpg.dx.aaa.user.util.constants.CUSTOM_ROLE_ID;

import org.cdpg.dx.aaa.user.dao.CustomRoleDAO;
import org.cdpg.dx.aaa.user.model.CustomRole;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class CustomRoleDAOImpl extends AbstractBaseDAO<CustomRole>
    implements CustomRoleDAO {

  public CustomRoleDAOImpl(PostgresService postgresService) {
    super(postgresService, "custom_role", CUSTOM_ROLE_ID, CustomRole::fromJson);
  }
}
