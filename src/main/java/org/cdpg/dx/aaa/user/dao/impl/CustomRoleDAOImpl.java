package org.cdpg.dx.aaa.user.dao.impl;

import static org.cdpg.dx.aaa.user.util.constants.CUSTOM_ROLE_ID;
import static org.cdpg.dx.aaa.user.util.constants.CUSTOM_ROLE_SCOPE_TABLE;

import org.cdpg.dx.aaa.user.dao.CustomRoleDAO;
import org.cdpg.dx.aaa.user.models.CustomRole;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class CustomRoleDAOImpl extends AbstractBaseDAO<CustomRole>
    implements CustomRoleDAO {

  public CustomRoleDAOImpl(PostgresService postgresService) {
    super(postgresService, CUSTOM_ROLE_SCOPE_TABLE, CUSTOM_ROLE_ID, CustomRole::fromJson);
  }
}
