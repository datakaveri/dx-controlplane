package org.cdpg.dx.aaa.delegation.dao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.util.UUID;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;
import org.cdpg.dx.database.postgres.models.QueryResult;

public interface DelegationGrantDAO extends BaseDAO<DelegationGrant> {
  Future<JsonArray> findActiveDelegationWithScopes(String delegatorId, String delegateeId);

  Future<JsonArray> rejectDelegation(String delegationId, String delegateId);

  Future<QueryResult> deactivate(UUID delegationId);
}
