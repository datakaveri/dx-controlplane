package org.cdpg.dx.aaa.summary.service.impl;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.summary.dao.UsageSummaryDao;
import org.cdpg.dx.aaa.summary.model.UsageSummary;
import org.cdpg.dx.aaa.summary.service.SummaryService;

import java.util.List;

public class SummaryServiceImpl implements SummaryService {
  private static final Logger LOGGER = LogManager.getLogger(SummaryServiceImpl.class);

  private final UsageSummaryDao usageSummaryDao;

  public SummaryServiceImpl(UsageSummaryDao usageSummaryDao) {
    this.usageSummaryDao = usageSummaryDao;
  }

  @Override
  public Future<List<UsageSummary>> getUsageSummaryForMainDashboard() {
    return usageSummaryDao.fetchAllUsageSummaries();
  }
}
