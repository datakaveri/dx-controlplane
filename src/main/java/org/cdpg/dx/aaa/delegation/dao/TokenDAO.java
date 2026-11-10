package org.cdpg.dx.aaa.delegation.dao;

import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.delegation.models.IssuedToken;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

public interface TokenDAO extends BaseDAO<IssuedToken> {
}
