package org.cdpg.dx.aaa.credit.dao.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.dao.CreditRequestDAO;
import org.cdpg.dx.aaa.credit.models.CreditRequest;

import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.credit.util.Constants.CREDIT_REQUEST_ID;
import static org.cdpg.dx.aaa.credit.util.Constants.CREDIT_REQUEST_TABLE;

public class CreditRequestDAOImpl extends AbstractBaseDAO<CreditRequest> implements CreditRequestDAO {
  private static final Logger LOGGER = LogManager.getLogger(CreditRequestDAOImpl.class);

  public CreditRequestDAOImpl(PostgresService postgresService)
  {
      super(postgresService, CREDIT_REQUEST_TABLE, CREDIT_REQUEST_ID, CreditRequest::fromJson);
  }

}
