package org.cdpg.dx.aaa.kyc.dao.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.kyc.dao.KYCTransactionDAO;
import org.cdpg.dx.aaa.kyc.model.KYCTransaction;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.kyc.util.Constants.KYC_TRANSACTION_TABLE;
import static org.cdpg.dx.aaa.kyc.util.Constants.USER_ID;

public class KYCTransactionDAOImpl extends AbstractBaseDAO<KYCTransaction> implements KYCTransactionDAO {
  private static final Logger LOGGER = LogManager.getLogger(KYCTransactionDAOImpl.class);

  public KYCTransactionDAOImpl(PostgresService postgresService)
  {
    super(postgresService, KYC_TRANSACTION_TABLE,USER_ID, KYCTransaction::fromJson);
  }
}

