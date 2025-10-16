package org.cdpg.dx.aaa.delegation.dao.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.dao.DelegationRequestDAO;
import org.cdpg.dx.aaa.delegation.models.DelegationUpdateRequest;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.delegation.util.Constants.*;

public class DelegationRequestDAOImpl extends AbstractBaseDAO<DelegationUpdateRequest> implements DelegationRequestDAO {

  private static final Logger LOGGER = LogManager.getLogger(DelegationRequestDAOImpl.class);

  public DelegationRequestDAOImpl(PostgresService postgresService) {
    super(postgresService, DELEGATION_REQUEST_TABLE, REQUEST_ID, DelegationUpdateRequest::fromJson);
  }
}
