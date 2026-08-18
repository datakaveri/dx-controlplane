package org.cdpg.dx.aaa.delegation.dao.impl;

import static org.cdpg.dx.aaa.delegation.util.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.dao.ScopeConstraintDAO;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ScopeConstraintDAOImpl extends AbstractBaseDAO<DelegationScopeConstraint> implements ScopeConstraintDAO {

  private static final Logger LOGGER = LogManager.getLogger(ScopeConstraintDAOImpl.class);

  public ScopeConstraintDAOImpl(PostgresService postgresService) {
    super(postgresService, DELEGATION_SCOPE_CONSTRAINT_TABLE, SCOPE_CONSTRAINT_ID, DelegationScopeConstraint::fromJson);
  }

  @Override
  public Future<Integer> deleteByConstraint(
      UUID delegationId, String role, String scope, String entityId, String entityType) {

    String sql =
        """
        DELETE FROM delegation_scope_constraints
        WHERE delegation_id = $1
          AND role = $2
          AND scope = $3
          AND entity_id = $4
          AND entity_type = $5
        """;

    JsonArray params =
        new JsonArray()
            .add(delegationId.toString())
            .add(role)
            .add(scope)
            .add(entityId)
            .add(entityType);

    return postgresService
        .executeQuery(sql, params)
        .map(result -> result.getRows().size())
        .recover(
            err -> {
              LOGGER.error(
                  "Failed to delete delegation constraint for delegationId={}, role={}, "
                      + "scope={}, entityId={}, entityType={}",
                  delegationId,
                  role,
                  scope,
                  entityId,
                  entityType,
                  err);
              return Future.failedFuture(BaseDxException.from(err));
            });
  }
}
