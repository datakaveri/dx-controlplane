package org.cdpg.dx.aaa.organization.factory;

import io.vertx.core.Vertx;
import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.orgReport.service.OrganizationCreateReportService;
import org.cdpg.dx.aaa.orgReport.service.impl.OrganizationCreateRequestReportServiceImpl;
import org.cdpg.dx.aaa.organization.controller.OrganizationReportController;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAOFactory;
import org.cdpg.dx.aaa.organization.handler.OrganizationReportHandler;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class OrganizationReportControllerFactory {

  public static OrganizationReportController create(Vertx vertx, PostgresService pgService) {

    OrganizationDAOFactory organizationDAOFactory = new OrganizationDAOFactory(pgService);
    CreditDAOFactory creditDAOFactory = new CreditDAOFactory(pgService);

    OrganizationCreateReportService organizationCreateReportService =
        new OrganizationCreateRequestReportServiceImpl(
            organizationDAOFactory, creditDAOFactory, vertx);

    OrganizationReportHandler organizationReportHandler =
        new OrganizationReportHandler(organizationCreateReportService);

    return new OrganizationReportController(organizationReportHandler);
  }
}
