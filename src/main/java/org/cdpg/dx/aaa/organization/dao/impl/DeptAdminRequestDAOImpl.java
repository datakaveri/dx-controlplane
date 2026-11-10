package org.cdpg.dx.aaa.organization.dao.impl;

import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.DeptAdminRequestDAO;
import org.cdpg.dx.aaa.organization.models.DeptAdminRequest;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class DeptAdminRequestDAOImpl extends AbstractBaseDAO<DeptAdminRequest>
    implements DeptAdminRequestDAO {

  public DeptAdminRequestDAOImpl(PostgresService postgresService) {
    super(
        postgresService,
        Constants.DEPT_ADMIN_REQUEST_TABLE,
        "id",
        DeptAdminRequest::fromJson);
  }
}
