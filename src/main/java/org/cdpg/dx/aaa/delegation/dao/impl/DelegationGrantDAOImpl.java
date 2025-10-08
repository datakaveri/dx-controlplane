package org.cdpg.dx.aaa.delegation.dao.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.dao.DelegationGrantDAO;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.delegation.util.Constants.*;


public class DelegationGrantDAOImpl extends AbstractBaseDAO<DelegationGrant> implements DelegationGrantDAO {

  private static final Logger LOGGER = LogManager.getLogger(DelegationGrantDAOImpl.class);

  public DelegationGrantDAOImpl(PostgresService postgresService) {
    super(postgresService, DELEGATION_GRANT_TABLE, DELEGATION_ID, DelegationGrant::fromJson);
  }

}
