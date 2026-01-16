package org.cdpg.dx.aaa.summary.factroy;

import org.cdpg.dx.aaa.summary.controller.SummaryController;
import org.cdpg.dx.aaa.summary.dao.UsageSummaryDao;
import org.cdpg.dx.aaa.summary.dao.impl.UsageSummaryDaoImpl;
import org.cdpg.dx.aaa.summary.service.SummaryService;
import org.cdpg.dx.aaa.summary.service.impl.SummaryServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class SummaryControllerFactory {

  public static SummaryController create(
      PostgresService postgresService, URNGenerator urnGenerator) {

    UsageSummaryDao usageSummaryDao = new UsageSummaryDaoImpl(postgresService, "usage_summary");
    SummaryService summaryService = new SummaryServiceImpl(usageSummaryDao);

    return new SummaryController(summaryService, urnGenerator);
  }
}
