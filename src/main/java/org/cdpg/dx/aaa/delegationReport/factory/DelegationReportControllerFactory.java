package org.cdpg.dx.aaa.delegationReport.factory;

import io.vertx.core.Vertx;
import org.cdpg.dx.aaa.delegation.dao.DelegationGrantDAO;
import org.cdpg.dx.aaa.delegation.dao.impl.DelegationGrantDAOImpl;
import org.cdpg.dx.aaa.delegationReport.controller.DelegationReportController;
import org.cdpg.dx.aaa.delegationReport.service.DelegationReportService;
import org.cdpg.dx.aaa.delegationReport.service.impl.DelegationReportServiceImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class DelegationReportControllerFactory {
  public static DelegationReportController create(
      PostgresService pgService, Vertx vertx) {
    DelegationGrantDAO delegationGrantDAO = new DelegationGrantDAOImpl(pgService);

    DelegationReportService delegationReportService =
        new DelegationReportServiceImpl(delegationGrantDAO, vertx);

    return new DelegationReportController(
        delegationReportService);
  }
}
