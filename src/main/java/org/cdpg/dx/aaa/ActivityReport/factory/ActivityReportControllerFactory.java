package org.cdpg.dx.aaa.ActivityReport.factory;

import io.vertx.core.Vertx;
import org.cdpg.dx.aaa.ActivityReport.controller.ActivityReportController;
import org.cdpg.dx.aaa.ActivityReport.dao.ActivityReportLogDao;
import org.cdpg.dx.aaa.ActivityReport.dao.impl.ActivityReportLogDaoImpl;
import org.cdpg.dx.aaa.ActivityReport.service.ActivityReportService;
import org.cdpg.dx.aaa.ActivityReport.service.impl.ActivityReportServiceImpl;
import org.cdpg.dx.auth.v2.factory.AuthHandlersV2;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ActivityReportControllerFactory {
  public static ActivityReportController create(
      PostgresService pgService, Vertx vertx, AuthHandlersV2 authV2) {
    ActivityReportLogDao activityLogDao = new ActivityReportLogDaoImpl(pgService);

    ActivityReportService activityReportService =
        new ActivityReportServiceImpl(activityLogDao, vertx);

    return new ActivityReportController(
        activityReportService, authV2.authentication(), authV2.authorization());
  }
}