package org.cdpg.dx.aaa.delegation.dao.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.dao.TokenDAO;
import org.cdpg.dx.aaa.delegation.models.IssuedToken;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.delegation.util.Constants.*;

public class TokenDAOImpl extends AbstractBaseDAO<IssuedToken> implements TokenDAO {

  private static final Logger LOGGER = LogManager.getLogger(TokenDAOImpl.class);

  public TokenDAOImpl(PostgresService postgresService) {
    super(postgresService, ISSUED_TOKEN_TABLE, JTI, IssuedToken::fromJson);
  }
}
