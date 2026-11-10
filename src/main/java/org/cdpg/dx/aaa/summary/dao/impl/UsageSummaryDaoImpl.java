package org.cdpg.dx.aaa.summary.dao.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.summary.dao.UsageSummaryDao;
import org.cdpg.dx.aaa.summary.model.UsageSummary;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.List;

public class UsageSummaryDaoImpl extends AbstractBaseDAO<UsageSummary> implements UsageSummaryDao {

  public UsageSummaryDaoImpl(PostgresService postgresService, String tableName) {
    super(postgresService, tableName, "id", UsageSummary::fromJson);
  }

  @Override
  public Future<List<UsageSummary>> fetchAllUsageSummaries() {
    return getAll();
  }
}
