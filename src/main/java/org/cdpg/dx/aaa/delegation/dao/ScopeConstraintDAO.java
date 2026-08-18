package org.cdpg.dx.aaa.delegation.dao;

import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

public interface ScopeConstraintDAO extends BaseDAO<DelegationScopeConstraint> {

  Future<Integer> deleteByConstraint(
      UUID delegationId, String role, String scope, String entityId, String entityType);
}
