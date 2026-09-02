package org.cdpg.dx.aaa.delegation.dao.impl;

import static org.cdpg.dx.aaa.delegation.util.Constants.*;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.dao.DelegationGrantDAO;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class DelegationGrantDAOImpl extends AbstractBaseDAO<DelegationGrant> implements DelegationGrantDAO {

  private static final Logger LOGGER = LogManager.getLogger(DelegationGrantDAOImpl.class);

  public DelegationGrantDAOImpl(PostgresService postgresService) {
    super(postgresService, DELEGATION_GRANT_TABLE, DELEGATION_ID, DelegationGrant::fromJson);
  }

  @Override
  public Future<JsonArray> findActiveDelegationWithScopes(String delegatorId, String delegateeId) {
    String sql = """
        SELECT
          dg.delegation_id,
          dg.delegator_id,
          dg.delegate_id,
          dg.justification,
          dg.expiry_at   AS delegation_expiry_at,
          dg.status,
          dg.created_at,
          dg.revoked_at,
          dsc.role,
          dsc.scope,
          dsc.entity_id,
          dsc.entity_type,
          dsc.expiry_at  AS constraint_expiry_at
        FROM delegation_grants dg
        JOIN delegation_scope_constraints dsc ON dg.delegation_id = dsc.delegation_id
        WHERE dg.delegator_id = $1::uuid
          AND dg.delegate_id  = $2::uuid
          AND dg.status = 'active'
          AND (dg.expiry_at  IS NULL OR dg.expiry_at  > NOW())
          AND (dsc.expiry_at IS NULL OR dsc.expiry_at > NOW())
        """;

    JsonArray params = new JsonArray().add(delegatorId).add(delegateeId);

    return postgresService.executeQuery(sql, params)
        .map(result -> result.getRows())
        .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }

  @Override
  public Future<JsonArray> rejectDelegation(String delegationId, String delegateId) {

    String sql =
        """
        UPDATE delegation_grants
        SET status = 'rejected'
        WHERE delegation_id = $1::uuid
          AND delegate_id = $2::uuid
          AND status = 'active'
        RETURNING *
        """;

    JsonArray params = new JsonArray().add(delegationId).add(delegateId);

    return postgresService
        .executeQuery(sql, params)
        .map(QueryResult::getRows)
        .recover(
            err -> {
              LOGGER.error(
                  "Failed to reject delegation {} by delegate {}", delegationId, delegateId, err);

              return Future.failedFuture(BaseDxException.from(err));
            });
  }

  public Future<QueryResult> deactivate(UUID delegationId) {

    UpdateQuery query =
        new UpdateQuery()
            .setTable("delegation_grants")
            .setColumns(List.of(DB_STATUS))
            .setValues(List.of("deleted"))
            .setCondition(
                new Condition()
                    .setColumn("delegation_id")
                    .setValues(List.of(delegationId.toString()))
                    .setOperator(Condition.Operator.EQUALS));

    return postgresService.update(query);
  }
}
