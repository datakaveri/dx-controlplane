package org.cdpg.dx.acl.apiserver;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.activity.factory.ActivityControllerFactory;
import org.cdpg.dx.aaa.activity.factory.ActivityFactory;
import org.cdpg.dx.aaa.activity.service.ActivityService;
import org.cdpg.dx.acl.accessReport.controller.AccessReportController;
import org.cdpg.dx.acl.accessReport.factory.AccessReportFactory;
import org.cdpg.dx.acl.accessRequest.controller.AccessRequestController;
import org.cdpg.dx.acl.accessRequest.factory.AccessRequestFactory;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.acl.aclEmailHelper.EmailComposer;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

import java.util.List;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.*;

public class ControllerFactory {
  private static final Logger LOGGER = LogManager.getLogger(ControllerFactory.class);

  private ControllerFactory() {}

  public static List<ApdApiController> createControllers(
      Vertx vertx, JsonObject config, URNGenerator urnGenerator) {

    final String docIndex = config.getString("docIndex");
    final String vocContext = config.getString("vocContext");

    PostgresService pgService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    DataBrokerService dataBrokerService =
        DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    EmailService emailService = EmailService.createProxy(vertx, EMAIL_SERVICE_ADDRESS);
    ElasticsearchService esService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);

    String auditingExchange = config.getString("auditingExchange");
    String routingKey = config.getString("auditingRoutingKey");
    boolean isRemoteAudit = config.getBoolean("isRemoteAudit", false);
      ActivityFactory.init(pgService);
    ActivityService activityService = ActivityFactory.getActivityService();
    AuditingHandler auditingHandler =
        new AuditingHandler(
            dataBrokerService, activityService, auditingExchange, routingKey, isRemoteAudit);

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);
    EmailComposer emailComposer = new EmailComposer(emailService, keycloakUserService, config);

    AccessRequestController accessRequestController =
        AccessRequestFactory.createAccessRequestController(
            pgService,
            esService,
            emailService,
            keycloakUserService,
            auditingHandler,
            config,
            urnGenerator);

    AccessReportController accessReportController = AccessReportFactory.create(pgService, vertx);

    //    final ListController listController =
    //        ListControllerFactory.createListController(esService, auditingHandler, docIndex);
    //    final SearchController searchController =
    //        SearchControllerFactory.createSearchController(esService, auditingHandler, docIndex);
    //    final ItemController itemController =
    //        ItemControllerFactory.createCrudController(
    //            auditingHandler, esService, docIndex, vocContext);

    // TODO create other controllers

    //    return List.of(
    ////        organizationController,
    ////        creditApiController,
    ////        kycController,
    ////        adminController,
    //        accessRequestController,
    //        accessReportController);
    ////        assetController,listController,searchController,itemController);

    return List.of(accessRequestController, accessReportController);
  }
}
