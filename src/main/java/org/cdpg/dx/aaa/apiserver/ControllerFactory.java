package org.cdpg.dx.aaa.apiserver;

import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.aaa.common.Constants.DOC_USER_INDEX;
import static org.cdpg.dx.aaa.common.Constants.VOC_CONTEXT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.REQUEST_TABLE;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.DATA_BROKER_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.ELASTIC_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.EMAIL_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.POSTGRES_SERVICE_ADDRESS;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;
import static org.cdpg.dx.database.elastic.util.Constants.VERIFIED_BY;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

import java.util.HashMap;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.ActivityReport.controller.ActivityReportController;
import org.cdpg.dx.aaa.ActivityReport.factory.ActivityReportControllerFactory;
import org.cdpg.dx.aaa.activity.controller.ActivityController;
import org.cdpg.dx.aaa.activity.factory.ActivityControllerFactory;
import org.cdpg.dx.aaa.activity.factory.ActivityFactory;
import org.cdpg.dx.aaa.activity.service.ActivityService;
import org.cdpg.dx.aaa.admin.controller.AdminController;
import org.cdpg.dx.aaa.admin.handler.AdminHandler;
import org.cdpg.dx.aaa.asset.controller.AssetController;
import org.cdpg.dx.aaa.asset.factory.AssetFactory;
import org.cdpg.dx.aaa.asset.handler.AssetHandler;
import org.cdpg.dx.aaa.clientSecret.controller.ClientController;
import org.cdpg.dx.aaa.clientSecret.factory.ClientControllerFactory;
import org.cdpg.dx.aaa.connector.service.ConnectorService;
import org.cdpg.dx.aaa.connector.service.ConnectorServiceImpl;
import org.cdpg.dx.aaa.credit.factory.CreditControllerFactory;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
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
import org.cdpg.dx.aaa.resourceserver.factory.ResourceServerControllerFactory;
import org.cdpg.dx.aaa.search.controller.SearchController;
import org.cdpg.dx.aaa.search.factory.SearchControllerFactory;
import org.cdpg.dx.aaa.token.controller.TokenController;
import org.cdpg.dx.aaa.token.factory.TokenControllerFactory;
import org.cdpg.dx.aaa.user.factory.UserControllerFactory;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.dao.impl.AccessRequestDaoImpl;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
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
    final String docIndex = config.getString(DOC_INDEX);
    final String docUserIndex = config.getString(DOC_USER_INDEX);
    final String vocContext = config.getString(VOC_CONTEXT);
    final Boolean kycRequired = config.getBoolean("kycRequired");
    final String apdURL = config.getString(APD_URL);
    final String verifiedBy = config.getString(VERIFIED_BY);

    WebClient webClient = WebClient.create(vertx);

    final HashMap<String, String> scriptConfig = new HashMap<>();
    scriptConfig.put("dataPlaneUrl", config.getString("dataPlaneUrl"));
    scriptConfig.put("controlPlaneUrl", config.getString("controlPlaneUrl"));
    scriptConfig.put("ogcDataPlaneUrl", config.getString("ogcDataPlaneUrl"));
    scriptConfig.put("bucketName", config.getString("bucketName"));
    scriptConfig.put("region", config.getString("region"));
    scriptConfig.put("s3BucketIdentifier", config.getString("s3BucketIdentifier"));

    PostgresService pgService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    DataBrokerService dataBrokerService =
        DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    EmailService emailService = EmailService.createProxy(vertx, EMAIL_SERVICE_ADDRESS);
    ElasticsearchService esService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);
    ActivityFactory.init(pgService);

    // Activity Controller
    ActivityController activityController =
        ActivityControllerFactory.create(pgService, urnGenerator);
    ActivityReportController activityReportController =
        ActivityReportControllerFactory.create(pgService, vertx);
    AccessRequestDao accessRequestDao =
        new AccessRequestDaoImpl(pgService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);

    String auditingExchange = config.getString("auditingExchange");
    String routingKey = config.getString("auditingRoutingKey");
    boolean isRemoteAudit = config.getBoolean("isRemoteAudit", false);

    ActivityService activityService = ActivityFactory.getActivityService();

    AuditingHandler auditingHandler =
        new AuditingHandler(
            dataBrokerService, activityService, auditingExchange, routingKey, isRemoteAudit);

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);

    PolicyDao policyDao = new PolicyDaoImpl(pgService);
    ItemService itemService =
        new ItemServiceImpl(esService, keycloakUserService, policyDao, webClient, docIndex, apdURL);

    CreditService creditService =
        CreditControllerFactory.createService(pgService, keycloakUserService, config);
    OrganizationService organizationService =
        OrganizationControllerFactory.createService(pgService, keycloakUserService, itemService);
    //    UserService userService =
    //        new UserServiceImpl(keycloakUserService, organizationService, creditService
    // ,esService,docIndex);

    UserService userService =
        UserControllerFactory.createService(
            keycloakUserService, organizationService, creditService, esService, docUserIndex);
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

    ApiController userController = UserControllerFactory.create(userService, urnGenerator);

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
            urnGenerator,
            kycRequired);

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
    IngestionService ingestionService = new IngestionServiceImpl(dataBrokerService);
    String publishExchange = config.getString("publishExchange");
    ConnectorService connectorService =
        new ConnectorServiceImpl(dataBrokerService, publishExchange);
    final ItemController itemController =
        ItemControllerFactory.createCrudController(
            auditingHandler, esService, pgService, keycloakUserService, docIndex, vocContext,
            apdURL,verifiedBy, urnGenerator, webClient,ingestionService,connectorService,scriptConfig);

    ApiController resourceServerController =
        ResourceServerControllerFactory.createController(pgService, auditingHandler, urnGenerator);

    ClientController clientController = ClientControllerFactory.create(pgService, urnGenerator);

    TokenController tokenController =
        TokenControllerFactory.create(
            pgService, esService, config, vertx, webClient, policyDao, urnGenerator);

    PublicController publicController = PublicKeycontrllerFactory.create(config, vertx);

    // ingestionService already created above for ItemController

    return List.of(
        organizationController,
        creditApiController,
        kycController,
        adminController,
        assetController,
        listController,
        searchController,
        itemController,
        resourceServerController,
        clientController,
        tokenController,
        publicController,
        userController,
        activityController,
        activityReportController);
  }
}
