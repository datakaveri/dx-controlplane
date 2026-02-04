package org.cdpg.dx.aaa.apiserver;

import static org.cdpg.dx.aaa.common.Constants.CENTRAL_CAT_DOC_INDEX;
import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.aaa.common.Constants.DOC_USER_INDEX;
import static org.cdpg.dx.aaa.common.Constants.IS_CENTRAL_CATALOGUE_ENABLED;
import static org.cdpg.dx.aaa.common.Constants.UPLOADED_BY;
import static org.cdpg.dx.aaa.common.Constants.VOC_CONTEXT;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.CENTRAL_ELASTIC_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.DATA_BROKER_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.ELASTIC_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.EMAIL_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.POSTGRES_SERVICE_ADDRESS;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.ActivityReport.controller.ActivityReportController;
import org.cdpg.dx.aaa.ActivityReport.factory.ActivityReportControllerFactory;
import org.cdpg.dx.aaa.admin.controller.AdminController;
import org.cdpg.dx.aaa.admin.handler.AdminHandler;
import org.cdpg.dx.aaa.appCredentials.factory.AppCredentialsControllerFactory;
import org.cdpg.dx.aaa.asset.controller.AssetController;
import org.cdpg.dx.aaa.asset.factory.AssetFactory;
import org.cdpg.dx.aaa.asset.handler.AssetHandler;
import org.cdpg.dx.aaa.bookmarks.factory.BookmarksControllerFactory;
import org.cdpg.dx.aaa.central.catalogue.list.controller.CentralListController;
import org.cdpg.dx.aaa.central.catalogue.list.factory.CentralListControllerFactory;
import org.cdpg.dx.aaa.central.catalogue.search.controller.CentralSearchController;
import org.cdpg.dx.aaa.central.catalogue.search.factory.CentralSearchControllerFactory;
import org.cdpg.dx.aaa.clientSecret.controller.ClientController;
import org.cdpg.dx.aaa.clientSecret.factory.ClientControllerFactory;
import org.cdpg.dx.aaa.connector.service.ConnectorService;
import org.cdpg.dx.aaa.connector.service.ConnectorServiceImpl;
import org.cdpg.dx.aaa.credit.factory.CreditControllerFactory;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.delegation.ItemOwnershipValidator;
import org.cdpg.dx.aaa.delegation.OrgOwnershipValidator;
import org.cdpg.dx.aaa.delegation.factory.DelegationControllerFactory;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.factory.EmailComposerFactory;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.ingestion.service.IngestionService;
import org.cdpg.dx.aaa.ingestion.service.IngestionServiceImpl;
import org.cdpg.dx.aaa.interaction.factory.UserInteractionControllerFactory;
import org.cdpg.dx.aaa.interaction.v2.factory.UserInteractionV2controllerFactory;
import org.cdpg.dx.aaa.item.controller.ItemController;
import org.cdpg.dx.aaa.item.factory.ItemControllerFactory;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.kyc.controller.KYCController;
import org.cdpg.dx.aaa.kyc.factory.KYCFactory;
import org.cdpg.dx.aaa.kyc.handler.KYCHandler;
import org.cdpg.dx.aaa.leaderboard.factory.LeaderboardControllerFactory;
import org.cdpg.dx.aaa.list.controller.ListController;
import org.cdpg.dx.aaa.list.factory.ListControllerFactory;
import org.cdpg.dx.aaa.organization.controller.OrganizationReportController;
import org.cdpg.dx.aaa.organization.factory.OrganizationControllerFactory;
import org.cdpg.dx.aaa.organization.factory.OrganizationReportControllerFactory;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.publicKey.controller.PublicController;
import org.cdpg.dx.aaa.publicKey.factory.PublicKeycontrllerFactory;
import org.cdpg.dx.aaa.resourceserver.factory.ResourceServerControllerFactory;
import org.cdpg.dx.aaa.search.controller.SearchController;
import org.cdpg.dx.aaa.search.factory.SearchControllerFactory;
import org.cdpg.dx.aaa.subscription.controller.SubscriptionController;
import org.cdpg.dx.aaa.subscription.factory.SubscriptionControllerFactory;
import org.cdpg.dx.aaa.summary.controller.SummaryController;
import org.cdpg.dx.aaa.summary.factroy.SummaryControllerFactory;
import org.cdpg.dx.aaa.token.controller.TokenController;
import org.cdpg.dx.aaa.token.factory.AppTokenControllerFactory;
import org.cdpg.dx.aaa.token.factory.TokenControllerFactory;
import org.cdpg.dx.aaa.user.dao.CustomRoleDAO;
import org.cdpg.dx.aaa.user.dao.impl.CustomRoleDAOImpl;
import org.cdpg.dx.aaa.user.factory.UserControllerFactory;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.aaa.vote.factory.VoteControllerFactory;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.aaa.activity.controller.ActivityController;
import org.cdpg.dx.aaa.activity.factory.ActivityControllerFactory;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.central.service.CentralElasticsearchService;
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
    final String centralCatDocIndex = config.getString(CENTRAL_CAT_DOC_INDEX);
    final String docUserIndex = config.getString(DOC_USER_INDEX);
    final String vocContext = config.getString(VOC_CONTEXT);
    final Boolean isKycRequired = config.getBoolean("kycRequired", false);
    final String apdURL = config.getString(APD_URL);
    final String uploadedBy = config.getString(UPLOADED_BY);

    WebClient webClient = WebClient.create(vertx);

    final String dataPlaneUrl = config.getString("dataPlaneUrl");
    final String controlPlaneUrl = config.getString("controlPlaneUrl");
    final String controlPlaneDomain = config.getString("controlPlaneDomain");
    final String ogcDataPlaneUrl = config.getString("ogcDataPlaneUrl");
    PostgresService pgService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    DataBrokerService dataBrokerService =
        DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    EmailService emailService = EmailService.createProxy(vertx, EMAIL_SERVICE_ADDRESS);
    ElasticsearchService esService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);

    // Activity Controller
    ActivityController activityController =
        ActivityControllerFactory.create(pgService, urnGenerator);
    ActivityReportController activityReportController =
        ActivityReportControllerFactory.create(pgService, vertx);

    String auditingExchange = config.getString("auditingExchange");
    String routingKey = config.getString("auditingRoutingKey");
    boolean isRemoteAudit = config.getBoolean("isRemoteAudit", false);

    AuditingHandler auditingHandler =
        new AuditingHandler(dataBrokerService, auditingExchange, routingKey, isRemoteAudit);

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);

    EmailComposer emailComposer =
        EmailComposerFactory.create(
            emailService, keycloakUserService, pgService, esService, webClient, config);

    PolicyDao policyDao = new PolicyDaoImpl(pgService);
    ItemService itemService =
        new ItemServiceImpl(esService, keycloakUserService, policyDao, webClient, docIndex, apdURL);

    ItemOwnershipValidator itemOwnershipValidator = new ItemOwnershipValidator(itemService);

    CreditService creditService =
        CreditControllerFactory.createService(pgService, keycloakUserService, config);

    OrganizationService organizationService =
        OrganizationControllerFactory.createService(pgService, keycloakUserService, itemService);

    DelegationService delegationService =
        DelegationControllerFactory.createService(
            pgService, keycloakUserService, organizationService, itemService);
    CustomRoleDAO customRoleDAO = new CustomRoleDAOImpl(pgService);
    UserService userService =
        UserControllerFactory.createService(
            keycloakUserService,
            organizationService,
            creditService,
            esService,
            customRoleDAO,
            docUserIndex);

    AssetHandler assetHandler =
        AssetFactory.createHandler(pgService, itemService, config, emailComposer, urnGenerator);
    ApiController assetController = new AssetController(assetHandler, auditingHandler);

    ApiController creditApiController =
        CreditControllerFactory.create(
            creditService,
            emailComposer,
            userService,
            organizationService,
            urnGenerator,
            isKycRequired);

    ApiController delegationApiController =
        DelegationControllerFactory.create(
            delegationService, emailComposer, userService, urnGenerator, keycloakUserService);

    ApiController userController = UserControllerFactory.create(userService, urnGenerator);

    KYCHandler kycHandler =
        KYCFactory.createHandler(vertx, config, creditService, pgService, urnGenerator);
    ApiController kycController = new KYCController(kycHandler);

    OrgOwnershipValidator orgOwnershipValidator = new OrgOwnershipValidator(organizationService);

    ApiController organizationController =
        OrganizationControllerFactory.create(
            userService,
            auditingHandler,
            emailComposer,
            pgService,
            esService,
            keycloakUserService,
            urnGenerator,
            delegationService,
            orgOwnershipValidator,
            webClient,
            isKycRequired,
            docIndex,
            apdURL);

    OrganizationReportController organizationReportController =
        OrganizationReportControllerFactory.create(vertx, pgService);

    AdminHandler adminHandler =
        new AdminHandler(
            userService,
            keycloakUserService,
            creditService,
            organizationService,
            urnGenerator,
            emailComposer);

    ApiController adminController = new AdminController(adminHandler);

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

    boolean isCentralCatEnabled = config.getBoolean(IS_CENTRAL_CATALOGUE_ENABLED, false);
    boolean isEdgeCatalogue = config.getBoolean("isEdgeCatalogue", false);
    boolean isStandalone = config.getBoolean("isStandalone", false);

    CentralElasticsearchService centralEsService = null;
    CentralSearchController centralSearchController = null;
    CentralListController centralListController = null;

    // Initialize central ES service
    if (isCentralCatEnabled) {
      LOGGER.debug(
          "Central catalogue mode enabled. Initializing CentralElasticsearchService and central controllers.");
      centralEsService =
          CentralElasticsearchService.createProxy(vertx, CENTRAL_ELASTIC_SERVICE_ADDRESS);

      centralSearchController =
          CentralSearchControllerFactory.createSearchController(
              centralEsService, auditingHandler, centralCatDocIndex, urnGenerator);

      centralListController =
          CentralListControllerFactory.createListController(
              centralEsService, auditingHandler, centralCatDocIndex, urnGenerator);
    }
    final ItemController itemController =
        ItemControllerFactory.createCrudController(
            auditingHandler,
            esService,
            centralEsService,
            pgService,
            keycloakUserService,
            itemOwnershipValidator,
            centralCatDocIndex,
            docIndex,
            vocContext,
            apdURL,
            uploadedBy,
            urnGenerator,
            webClient,
            ingestionService,
            connectorService,
            dataPlaneUrl,
            controlPlaneUrl,
            ogcDataPlaneUrl,
            isCentralCatEnabled,
            isEdgeCatalogue,
            isStandalone,
            delegationService);

    ApiController resourceServerController =
        ResourceServerControllerFactory.createController(pgService, auditingHandler, urnGenerator);

    ClientController clientController = ClientControllerFactory.create(pgService, urnGenerator);

    TokenController tokenController =
        TokenControllerFactory.create(
            pgService,
            esService,
            config,
            vertx,
            webClient,
            policyDao,
            delegationService,
            urnGenerator);

    PublicController publicController = PublicKeycontrllerFactory.create(config, vertx);

    // ingestionService already created above for ItemController

    SubscriptionController subscriptionController =
        SubscriptionControllerFactory.create(
            dataBrokerService, pgService, urnGenerator, controlPlaneDomain);
    ApiController bookmarksController = BookmarksControllerFactory.create(pgService, urnGenerator);
    ApiController appCredentialsController =
        AppCredentialsControllerFactory.create(
            pgService, organizationService, itemService, urnGenerator);

    ApiController appTokenController =
        AppTokenControllerFactory.create(
            pgService,
            keycloakUserService,
            organizationService,
            itemService,
            urnGenerator,
            config,
            vertx);

    SummaryController dashboardSummaryController =
        SummaryControllerFactory.create(pgService, urnGenerator);

    ApiController voteController = VoteControllerFactory.create(pgService, urnGenerator);

    List<ApiController> controllers = new ArrayList<>();

    controllers.add(organizationController);
    controllers.add(organizationReportController);
    controllers.add(creditApiController);
    controllers.add(kycController);
    controllers.add(adminController);
    controllers.add(assetController);
    controllers.add(listController);
    controllers.add(searchController);
    controllers.add(itemController);
    controllers.add(resourceServerController);
    controllers.add(clientController);
    controllers.add(tokenController);
    controllers.add(publicController);
    controllers.add(userController);
    controllers.add(delegationApiController);
    controllers.add(activityController);
    controllers.add(activityReportController);
    controllers.add(subscriptionController);
    // controllers.add(bookmarksController);
    controllers.add(appCredentialsController);
    controllers.add(appTokenController);

    // Add central controllers only if enabled
    if (isCentralCatEnabled) {
      controllers.add(centralListController);
      controllers.add(centralSearchController);
    }
    controllers.add(dashboardSummaryController);
    // controllers.add(voteController);
    ApiController leaderboardController =
        LeaderboardControllerFactory.create(pgService, urnGenerator);
    controllers.add(leaderboardController);

    ApiController userInteractionController =
        UserInteractionControllerFactory.create(pgService, itemService, urnGenerator);
    // controllers.add(userInteractionController);

    ApiController userV2InteractionApi =
        UserInteractionV2controllerFactory.create(
            pgService, itemService, auditingHandler, urnGenerator);
    controllers.add(userV2InteractionApi);

    return controllers;
  }
}
