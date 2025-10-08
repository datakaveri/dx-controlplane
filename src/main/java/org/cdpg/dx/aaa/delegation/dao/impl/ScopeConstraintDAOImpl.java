package org.cdpg.dx.aaa.delegation.dao.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.dao.ScopeConstraintDAO;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.delegation.util.Constants.*;

public class ScopeConstraintDAOImpl extends AbstractBaseDAO<DelegationScopeConstraint> implements ScopeConstraintDAO {

  private static final Logger LOGGER = LogManager.getLogger(ScopeConstraintDAOImpl.class);

  public ScopeConstraintDAOImpl(PostgresService postgresService) {
    super(postgresService, DELEGATION_SCOPE_CONSTRAINT_TABLE, SCOPE_CONSTRAINT_ID, DelegationScopeConstraint::fromJson);
  }
}
