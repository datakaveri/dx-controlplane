package org.cdpg.dx.aaa.delegation.dao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

public interface DelegationGrantDAO extends BaseDAO<DelegationGrant> {
  Future<JsonArray> findActiveDelegationWithScopes(String delegatorId, String delegateeId);
}
