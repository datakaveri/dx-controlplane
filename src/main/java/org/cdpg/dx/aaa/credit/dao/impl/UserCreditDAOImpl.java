package org.cdpg.dx.aaa.credit.dao.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.dao.UserCreditDAO;
import org.cdpg.dx.aaa.credit.models.UserCredit;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.credit.util.Constants.*;

public class UserCreditDAOImpl extends AbstractBaseDAO<UserCredit>implements UserCreditDAO {
  private static final Logger LOGGER = LogManager.getLogger(UserCreditDAOImpl.class);

  public UserCreditDAOImpl(PostgresService postgresService) {
    super(postgresService,USER_CREDIT_TABLE, USER_ID, UserCredit::fromJson);
  }

}



