package org.cdpg.dx.aaa.credit.dao.impl;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.dao.ComputeRoleDAO;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.util.Constants;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.List;
import java.util.UUID;

import static org.cdpg.dx.aaa.credit.util.Constants.COMPUTE_ROLE_ID;

public class ComputeRoleDAOImpl extends AbstractBaseDAO<ComputeRole>implements ComputeRoleDAO {

  private static final Logger LOGGER = LogManager.getLogger(ComputeRoleDAOImpl.class);

  public ComputeRoleDAOImpl(PostgresService postgresService)
  {
    super(postgresService, Constants.COMPUTE_ROLE_TABLE, COMPUTE_ROLE_ID, ComputeRole::fromJson);
  }

  @Override
  public Future<Boolean> hasUserComputeAccess(UUID userId) {

    SelectQuery query = new SelectQuery(
      Constants.COMPUTE_ROLE_TABLE,
      List.of(Constants.STATUS),
      new Condition(Constants.USER_ID, Condition.Operator.EQUALS, List.of(userId.toString())),
      null, null, null, null
    );

    return postgresService.select(query, false)
      .compose(result -> {
      if(!result.getRows().isEmpty())
      {
        String status = result.getRows().getJsonObject(0).getString(Constants.STATUS);
        if(status.equals(Status.GRANTED.getStatus()))
        {
          return Future.succeededFuture(true);
        }
      }
      return Future.succeededFuture(false);
      })
      .recover(err -> {
        LOGGER.error("Error fetching computerole access status for user {}: {}", userId, err.getMessage(), err);
        return Future.failedFuture(err);
      });
  }
}
