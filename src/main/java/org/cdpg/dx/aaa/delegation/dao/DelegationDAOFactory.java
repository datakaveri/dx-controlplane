package org.cdpg.dx.aaa.delegation.dao;

import org.cdpg.dx.aaa.delegation.dao.impl.DelegationGrantDAOImpl;
import org.cdpg.dx.aaa.delegation.dao.impl.DelegationRequestDAOImpl;
import org.cdpg.dx.aaa.delegation.dao.impl.ScopeConstraintDAOImpl;
import org.cdpg.dx.aaa.delegation.dao.impl.TokenDAOImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class DelegationDAOFactory {

    private final PostgresService postgresService;

    public DelegationDAOFactory(PostgresService postgresService)
    {
      this.postgresService=postgresService;
    }

    public DelegationGrantDAO delegationGrantDAO() {
      return new DelegationGrantDAOImpl(postgresService);
    }

    public DelegationRequestDAO delegationRequestDAO() {
      return new DelegationRequestDAOImpl(postgresService);
    }

    public ScopeConstraintDAO scopeConstraintDAO() {
      return new ScopeConstraintDAOImpl(postgresService);
    }
    public TokenDAO tokenDAO() {
      return new TokenDAOImpl(postgresService) {
      };
    }
}
