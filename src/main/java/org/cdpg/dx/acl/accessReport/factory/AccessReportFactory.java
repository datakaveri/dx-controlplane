package org.cdpg.dx.acl.accessReport.factory;

import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.REQUEST_TABLE;

import io.vertx.core.Vertx;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.dao.impl.AccessRequestDaoImpl;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessReport.controller.AccessReportController;
import org.cdpg.dx.acl.accessReport.service.impl.ReportServiceImpl;
import org.cdpg.dx.auth.v2.factory.AuthHandlersV2;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AccessReportFactory {
  public static AccessReportController create(
      PostgresService pgService, Vertx vertx, AuthHandlersV2 authV2) {
    AccessRequestDao accessRequestDao =
        new AccessRequestDaoImpl(pgService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);
    return new AccessReportController(
        new ReportServiceImpl(accessRequestDao, vertx),
        authV2.authorization());
  }
}
