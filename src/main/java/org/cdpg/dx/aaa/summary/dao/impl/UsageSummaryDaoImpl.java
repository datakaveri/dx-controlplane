package org.cdpg.dx.aaa.summary.dao.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.activity.model.ActivityAuditLogEntity;
import org.cdpg.dx.aaa.summary.dao.UsageSummaryDao;
import org.cdpg.dx.aaa.summary.model.UsageSummary;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.function.Function;

import static org.cdpg.dx.aaa.activity.util.ActivityConstants.ID;

public class UsageSummaryDaoImpl extends AbstractBaseDAO<UsageSummary> implements UsageSummaryDao {

  public UsageSummaryDaoImpl(PostgresService postgresService, String tableName) {
    super(postgresService, tableName, ID, UsageSummary::fromJson);
  }

  @Override
  public Future<List<UsageSummary>> fetchAllUsageSummaries() {
    return getAll();
  }
}
