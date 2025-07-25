package org.cdpg.dx.aaa.credit.dao.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.dao.CreditTransactionDAO;
import org.cdpg.dx.aaa.credit.models.CreditTransaction;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.credit.util.Constants.CREDIT_TRANSACTION_ID;
import static org.cdpg.dx.aaa.credit.util.Constants.CREDIT_TRANSACTION_TABLE;

public class CreditTransactionDAOImpl extends AbstractBaseDAO<CreditTransaction> implements CreditTransactionDAO {

  private static final Logger LOGGER = LogManager.getLogger(CreditTransactionDAOImpl.class);

  public CreditTransactionDAOImpl(PostgresService postgresService) {
    super(postgresService,CREDIT_TRANSACTION_TABLE, CREDIT_TRANSACTION_ID, CreditTransaction::fromJson);
  }

}
