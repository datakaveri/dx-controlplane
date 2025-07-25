package org.cdpg.dx.aaa.accessReport.dao.impl;

import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.REQUEST_TABLE;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.aaa.accessReport.dao.AccessRequestDao;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AccessRequestDaoImpl extends AbstractBaseDAO<AccessRequestDto>
    implements AccessRequestDao {
  private static final Logger LOGGER = LogManager.getLogger(AccessRequestDaoImpl.class);

  public AccessRequestDaoImpl(PostgresService postgresService) {
    super(postgresService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);
  }
}
