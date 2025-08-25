package org.cdpg.dx.aaa.apiserver;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.*;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.ActivityReport.controller.ActivityReportController;
import org.cdpg.dx.aaa.ActivityReport.factory.ActivityReportControllerFactory;
// import org.cdpg.dx.aaa.accessReport.controller.AccessReportController;
// import org.cdpg.dx.aaa.accessReport.factory.AccessReportFactory;
// import org.cdpg.dx.aaa.accessRequest.controller.AccessRequestController;
// import org.cdpg.dx.aaa.accessRequest.factory.AccessRequestFactory;
import org.cdpg.dx.aaa.activity.controller.ActivityController;
import org.cdpg.dx.aaa.activity.factory.ActivityControllerFactory;
import org.cdpg.dx.aaa.admin.controller.AdminController;
import org.cdpg.dx.aaa.admin.handler.AdminHandler;
import org.cdpg.dx.aaa.asset.controller.AssetController;
import org.cdpg.dx.aaa.asset.factory.AssetFactory;
import org.cdpg.dx.aaa.asset.handler.AssetHandler;
import org.cdpg.dx.aaa.clientSecret.controller.ClientController;
import org.cdpg.dx.aaa.clientSecret.factory.ClientControllerFactory;
import org.cdpg.dx.aaa.credit.factory.CreditControllerFactory;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.ingestion.controller.IngestionAdaptorController;
import org.cdpg.dx.aaa.ingestion.service.IngestionService;
import org.cdpg.dx.aaa.ingestion.service.IngestionServiceImpl;
import org.cdpg.dx.aaa.item.controller.ItemController;
import org.cdpg.dx.aaa.item.factory.ItemControllerFactory;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.kyc.controller.KYCController;
import org.cdpg.dx.aaa.kyc.factory.KYCFactory;
import org.cdpg.dx.aaa.kyc.handler.KYCHandler;
import org.cdpg.dx.aaa.list.controller.ListController;
import org.cdpg.dx.aaa.list.factory.ListControllerFactory;
import org.cdpg.dx.aaa.organization.factory.OrganizationControllerFactory;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.publicKey.controller.PublicController;
import org.cdpg.dx.aaa.publicKey.factory.PublicKeycontrllerFactory;
import org.cdpg.dx.aaa.search.controller.SearchController;
import org.cdpg.dx.aaa.search.factory.SearchControllerFactory;
import org.cdpg.dx.aaa.token.controller.TokenController;
import org.cdpg.dx.aaa.token.factory.TokenControllerFactory;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.aaa.user.service.UserServiceImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class ControllerFactory {
  private static final Logger LOGGER = LogManager.getLogger(ControllerFactory.class);

  private ControllerFactory() {}

  public static List<ApiController> createControllers(
      Vertx vertx, JsonObject config, URNGenerator urnGenerator) {

    final String docIndex = config.getString("docIndex");
    final String vocContext = config.getString("vocContext");

    PostgresService pgService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    DataBrokerService dataBrokerService =
        DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    EmailService emailService = EmailService.createProxy(vertx, EMAIL_SERVICE_ADDRESS);
    ElasticsearchService esService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);

    ItemService itemService = new ItemServiceImpl(esService, docIndex);

    String auditingExchange = config.getString("auditingExchange");
    String routingKey = config.getString("auditingRoutingKey");

    AuditingHandler auditingHandler =
        new AuditingHandler(dataBrokerService, auditingExchange, routingKey);

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);
    CreditService creditService =
        CreditControllerFactory.createService(pgService, keycloakUserService, config);
    OrganizationService organizationService =
        OrganizationControllerFactory.createService(pgService, keycloakUserService, itemService);
    UserService userService =
        new UserServiceImpl(keycloakUserService, organizationService, creditService);
    EmailComposer emailComposer =
        new EmailComposer(
            emailService,
            keycloakUserService,
            config,
            organizationService,
            userService,
            creditService);

    AssetHandler assetHandler =
        AssetFactory.createHandler(pgService, config, emailComposer, urnGenerator);
    ApiController assetController = new AssetController(assetHandler, auditingHandler);

    ApiController creditApiController =
        CreditControllerFactory.create(creditService, emailComposer, userService, urnGenerator);
    KYCHandler kycHandler =
        KYCFactory.createHandler(vertx, config, creditService, pgService, urnGenerator);
    ApiController kycController = new KYCController(kycHandler);
    ApiController organizationController =
        OrganizationControllerFactory.create(
            organizationService,
            userService,
            auditingHandler,
            emailComposer,
            vertx,
            pgService,
            creditService,
            keycloakUserService,
            urnGenerator);

    AdminHandler adminHandler =
        new AdminHandler(
            userService, keycloakUserService, creditService, organizationService, urnGenerator);
    ApiController adminController = new AdminController(adminHandler);

    //    AccessRequestController accessRequestController =
    //      AccessRequestFactory.createAccessRequestController(
    //        pgService, esService, emailService, keycloakUserService, auditingHandler, config);
    //
    //    AccessReportController accessReportController = AccessReportFactory.create(pgService,
    // vertx);

    final ListController listController =
        ListControllerFactory.createListController(
            esService, auditingHandler, docIndex, urnGenerator);
    final SearchController searchController =
        SearchControllerFactory.createSearchController(
            esService, auditingHandler, docIndex, urnGenerator);
    final ItemController itemController =
        ItemControllerFactory.createCrudController(
            auditingHandler, esService, docIndex, vocContext, urnGenerator);

    // TODO create other controllers

    ClientController clientController = ClientControllerFactory.create(pgService, urnGenerator);

    TokenController tokenController =
        TokenControllerFactory.create(pgService, config, vertx, urnGenerator);

    PublicController publicController = PublicKeycontrllerFactory.create(config, vertx);

    // Activity Controller
    ActivityController activityController =
        ActivityControllerFactory.create(pgService, urnGenerator);
    ActivityReportController activityReportController =
        ActivityReportControllerFactory.create(pgService, vertx);
    IngestionService ingestionService = new IngestionServiceImpl(dataBrokerService);
    IngestionAdaptorController ingestionAdaptorController =
        new IngestionAdaptorController(ingestionService);

    return List.of(
        organizationController,
        creditApiController,
        kycController,
        adminController,
        assetController,
        listController,
        searchController,
        itemController,
        clientController,
        tokenController,
        publicController,
        activityController,
        activityReportController,
        ingestionAdaptorController);
  }
}
