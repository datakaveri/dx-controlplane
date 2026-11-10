package org.cdpg.dx.aaa.summary.service.impl;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.summary.dao.UsageAggregationDao;
import org.cdpg.dx.aaa.summary.dao.impl.UsageSummaryUpdaterDao;

public class UsageSummaryUpdateService {
  private static final Logger LOGGER = LogManager.getLogger(UsageSummaryUpdateService.class);

  private final UsageAggregationDao aggregationDao;
  private final UsageSummaryUpdaterDao updaterDao;

  public UsageSummaryUpdateService(
      UsageAggregationDao aggregationDao, UsageSummaryUpdaterDao updaterDao) {
    this.aggregationDao = aggregationDao;
    this.updaterDao = updaterDao;
  }

  public Future<Void> refresh() {
    return aggregationDao
        .aggregateUsage()
        .compose(updaterDao::upsert)
        .onSuccess(v -> LOGGER.info("Usage summary refreshed"))
        .onFailure(err -> LOGGER.error("Usage summary refresh failed", err));
  }
}
