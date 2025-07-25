package org.cdpg.dx.aaa.accessReport.factory;

import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.REQUEST_TABLE;

import io.vertx.core.Vertx;
import org.cdpg.dx.aaa.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.aaa.accessRequest.dao.impl.AccessRequestDaoImpl;
import org.cdpg.dx.aaa.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.aaa.accessReport.controller.AccessReportController;
import org.cdpg.dx.aaa.accessReport.service.impl.ReportServiceImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AccessReportFactory {
  public static AccessReportController create(PostgresService pgService, Vertx vertx) {
    AccessRequestDao accessRequestDao =
        new AccessRequestDaoImpl(pgService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);
    return new AccessReportController(new ReportServiceImpl(accessRequestDao, vertx));
  }
}
