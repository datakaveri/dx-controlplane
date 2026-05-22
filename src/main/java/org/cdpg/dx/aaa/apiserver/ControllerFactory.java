package org.cdpg.dx.aaa.apiserver;

import static org.cdpg.dx.aaa.common.Constants.CENTRAL_CAT_DOC_INDEX;
import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.aaa.common.Constants.IS_CENTRAL_CATALOGUE_ENABLED;
import static org.cdpg.dx.aaa.common.Constants.UPLOADED_BY;
import static org.cdpg.dx.aaa.common.Constants.VOC_CONTEXT;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.ActivityReport.controller.ActivityReportController;
import org.cdpg.dx.aaa.ActivityReport.factory.ActivityReportControllerFactory;
import org.cdpg.dx.aaa.aclserver.factory.AclServerControllerFactory;
import org.cdpg.dx.aaa.activity.controller.ActivityController;
import org.cdpg.dx.aaa.activity.factory.ActivityControllerFactory;
import org.cdpg.dx.aaa.admin.controller.AdminController;
import org.cdpg.dx.aaa.admin.handler.AdminHandler;
import org.cdpg.dx.aaa.appCredentials.factory.AppCredentialsControllerFactory;
import org.cdpg.dx.aaa.asset.controller.AssetController;
import org.cdpg.dx.aaa.asset.factory.AssetFactory;
import org.cdpg.dx.aaa.asset.handler.AssetHandler;
import org.cdpg.dx.aaa.central.catalogue.list.controller.CentralListController;
import org.cdpg.dx.aaa.central.catalogue.list.factory.CentralListControllerFactory;
import org.cdpg.dx.aaa.central.catalogue.search.controller.CentralSearchController;
import org.cdpg.dx.aaa.central.catalogue.search.factory.CentralSearchControllerFactory;
import org.cdpg.dx.aaa.clientSecret.controller.ClientController;
import org.cdpg.dx.aaa.clientSecret.factory.ClientControllerFactory;
import org.cdpg.dx.aaa.connector.service.ConnectorService;
import org.cdpg.dx.aaa.connector.service.ConnectorServiceImpl;
import org.cdpg.dx.aaa.conversation.factory.ConversationControllerFactory;
import org.cdpg.dx.aaa.credit.factory.CreditControllerFactory;
import org.cdpg.dx.aaa.delegation.ItemOwnershipValidator;
import org.cdpg.dx.aaa.delegation.OrgOwnershipValidator;
import org.cdpg.dx.aaa.delegation.factory.DelegationControllerFactory;
import org.cdpg.dx.aaa.ingestion.service.IngestionService;
import org.cdpg.dx.aaa.ingestion.service.IngestionServiceImpl;
import org.cdpg.dx.aaa.interaction.v2.factory.UserInteractionV2controllerFactory;
import org.cdpg.dx.aaa.item.controller.ItemController;
import org.cdpg.dx.aaa.item.factory.ItemControllerFactory;
import org.cdpg.dx.aaa.kyc.controller.KYCController;
import org.cdpg.dx.aaa.kyc.factory.KYCFactory;
import org.cdpg.dx.aaa.kyc.handler.KYCHandler;
import org.cdpg.dx.aaa.leaderboard.factory.LeaderboardControllerFactory;
import org.cdpg.dx.aaa.list.controller.ListController;
import org.cdpg.dx.aaa.list.factory.ListControllerFactory;
import org.cdpg.dx.aaa.organization.controller.OrganizationReportController;
import org.cdpg.dx.aaa.organization.factory.OrganizationControllerFactory;
import org.cdpg.dx.aaa.organization.factory.OrganizationReportControllerFactory;
import org.cdpg.dx.aaa.publicKey.controller.PublicController;
import org.cdpg.dx.aaa.publicKey.factory.PublicKeycontrllerFactory;
import org.cdpg.dx.aaa.resourceserver.factory.ResourceServerControllerFactory;
import org.cdpg.dx.aaa.search.controller.SearchController;
import org.cdpg.dx.aaa.search.factory.SearchControllerFactory;
import org.cdpg.dx.aaa.shareAssets.factory.VisibilityControllerFactory;
import org.cdpg.dx.aaa.subscription.controller.SubscriptionController;
import org.cdpg.dx.aaa.subscription.factory.SubscriptionControllerFactory;
import org.cdpg.dx.aaa.summary.controller.SummaryController;
import org.cdpg.dx.aaa.summary.factroy.SummaryControllerFactory;
import org.cdpg.dx.aaa.token.controller.TokenController;
import org.cdpg.dx.aaa.token.factory.AppTokenControllerFactory;
import org.cdpg.dx.aaa.token.factory.TokenControllerFactory;
import org.cdpg.dx.aaa.user.factory.UserControllerFactory;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auth.authentication.handler.AuthenticationHandler;
import org.cdpg.dx.common.URNGenerator;

/**
 * Creates and wires all API controllers for the application.
 *
 * <p>Uses {@link InfrastructureServices} for low-level service proxies and {@link SharedServices}
 * for domain services, eliminating the need to pass dozens of individual parameters.
 */
public class ControllerFactory {
  private static final Logger LOGGER = LogManager.getLogger(ControllerFactory.class);

  private ControllerFactory() {}

  public static List<ApiController> createControllers(
      Vertx vertx, JsonObject config, URNGenerator urnGenerator) {

    // ── Config values ──
    boolean isCentralCatEnabled = config.getBoolean(IS_CENTRAL_CATALOGUE_ENABLED, false);

    // ── Infrastructure & shared services ──
    InfrastructureServices infra = InfrastructureServices.create(vertx, isCentralCatEnabled);
    SharedServices shared = SharedServices.create(infra, config);

    final String docIndex = config.getString(DOC_INDEX);
    final String centralCatDocIndex = config.getString(CENTRAL_CAT_DOC_INDEX);
    final String vocContext = config.getString(VOC_CONTEXT);
    final String apdURL = config.getString(APD_URL);
    final String uploadedBy = config.getString(UPLOADED_BY);
    final String dataPlaneUrl = config.getString("dataPlaneUrl");
    final String controlPlaneUrl = config.getString("controlPlaneUrl");
    final String controlPlaneDomain = config.getString("controlPlaneDomain");
    final String ogcDataPlaneUrl = config.getString("ogcDataPlaneUrl");
    final Boolean isKycRequired = config.getBoolean("kycRequired", false);
    boolean isEdgeCatalogue = config.getBoolean("isEdgeCatalogue", false);
    boolean isStandalone = config.getBoolean("isStandalone", false);

    // ── Validators ──
    ItemOwnershipValidator itemOwnershipValidator =
        new ItemOwnershipValidator(shared.itemService());
    OrgOwnershipValidator orgOwnershipValidator =
        new OrgOwnershipValidator(shared.organizationService());

    // ── Messaging services ──
    IngestionService ingestionService = new IngestionServiceImpl(infra.dataBrokerService());
    String publishExchange = config.getString("publishExchange");
    ConnectorService connectorService =
        new ConnectorServiceImpl(infra.dataBrokerService(), publishExchange);

    // ── Controllers ──
    List<ApiController> controllers = new ArrayList<>();

    // Activity
    ActivityController activityController =
        ActivityControllerFactory.create(infra.pgService(), urnGenerator);
    controllers.add(activityController);

    ActivityReportController activityReportController =
        ActivityReportControllerFactory.create(infra.pgService(), vertx);
    controllers.add(activityReportController);

    // Organization
    ApiController organizationController =
        OrganizationControllerFactory.create(
            shared.userService(),
            shared.auditingHandler(),
            shared.emailComposer(),
            infra.pgService(),
            infra.esService(),
            shared.keycloakUserService(),
            urnGenerator,
            infra.webClient(),
            isKycRequired,
            docIndex,
            apdURL);
    controllers.add(organizationController);

    OrganizationReportController organizationReportController =
        OrganizationReportControllerFactory.create(vertx, infra.pgService());
    controllers.add(organizationReportController);

    // Credit & KYC
    ApiController creditApiController =
        CreditControllerFactory.create(
            shared.creditService(),
            shared.emailComposer(),
            shared.userService(),
            shared.organizationService(),
            shared.keycloakUserService(),
            shared.auditingHandler(),
            urnGenerator,
            isKycRequired);
    controllers.add(creditApiController);

    KYCHandler kycHandler =
        KYCFactory.createHandler(
            vertx, config, shared.creditService(), infra.pgService(), urnGenerator);
    controllers.add(new KYCController(kycHandler, shared.auditingHandler()));

    // Admin
    AdminHandler adminHandler =
        new AdminHandler(
            shared.userService(),
            shared.keycloakUserService(),
            shared.creditService(),
            shared.organizationService(),
            urnGenerator,
            shared.emailComposer());
    controllers.add(new AdminController(adminHandler));

    // Asset
    AssetHandler assetHandler =
        AssetFactory.createHandler(
            infra.pgService(), shared.itemService(), config, shared.emailComposer(), urnGenerator);
    controllers.add(new AssetController(assetHandler, shared.auditingHandler()));

    // Catalogue (list, search, item CRUD)
    ListController listController =
        ListControllerFactory.createListController(
            infra.esService(),
            shared.keycloakUserService(),
            shared.auditingHandler(),
            docIndex,
            urnGenerator);
    controllers.add(listController);

    SearchController searchController =
        SearchControllerFactory.createSearchController(
            infra.esService(),
            shared.keycloakUserService(),
            shared.auditingHandler(),
            docIndex,
            urnGenerator);
    controllers.add(searchController);

    // Central catalogue (optional)
    if (isCentralCatEnabled) {
      LOGGER.debug("Central catalogue mode enabled.");

      CentralSearchController centralSearchController =
          CentralSearchControllerFactory.createSearchController(
              infra.centralEsService(),
              shared.keycloakUserService(),
              shared.auditingHandler(),
              centralCatDocIndex,
              urnGenerator);
      controllers.add(centralSearchController);

      CentralListController centralListController =
          CentralListControllerFactory.createListController(
              infra.centralEsService(),
              shared.keycloakUserService(),
              shared.auditingHandler(),
              centralCatDocIndex,
              urnGenerator);
      controllers.add(centralListController);
    }

    ItemController itemController =
        ItemControllerFactory.createCrudController(
            shared.auditingHandler(),
            infra.esService(),
            infra.centralEsService(),
            infra.pgService(),
            shared.keycloakUserService(),
            itemOwnershipValidator,
            centralCatDocIndex,
            docIndex,
            vocContext,
            apdURL,
            uploadedBy,
            urnGenerator,
            infra.webClient(),
            ingestionService,
            connectorService,
            dataPlaneUrl,
            controlPlaneUrl,
            ogcDataPlaneUrl,
            isCentralCatEnabled,
            isEdgeCatalogue,
            isStandalone,
            shared.delegationService());
    controllers.add(itemController);

    // Acl server
    controllers.add(
        AclServerControllerFactory.createController(
            infra.pgService(), shared.auditingHandler(), urnGenerator));

    // Resource server
    controllers.add(
        ResourceServerControllerFactory.createController(
            infra.pgService(), shared.auditingHandler(), urnGenerator));

    //Share Assets
    controllers.add(
        VisibilityControllerFactory.createController(infra.pgService(), infra.esService(),
            docIndex, urnGenerator)
    );

    // Client secrets
    controllers.add(ClientControllerFactory.create(infra.pgService(), urnGenerator));

    // Token
    TokenController tokenController =
        TokenControllerFactory.create(
            infra.pgService(),
            infra.esService(),
            config,
            vertx,
            infra.webClient(),
            shared.policyDao(),
            shared.delegationService(),
            urnGenerator);
    controllers.add(tokenController);

    // Public keys
    controllers.add(PublicKeycontrllerFactory.create(config, vertx));

    // User
    controllers.add(UserControllerFactory.create(shared.userService(), urnGenerator));

    // Delegation
    controllers.add(
        DelegationControllerFactory.create(
            shared.delegationService(),
            shared.emailComposer(),
            shared.userService(),
            urnGenerator,
            shared.keycloakUserService()));

    // Subscription
    controllers.add(
        SubscriptionControllerFactory.create(
            shared.auditingHandler(),
            infra.dataBrokerService(),
            infra.pgService(),
            urnGenerator,
            controlPlaneDomain));

    // App credentials & tokens
    controllers.add(
        AppCredentialsControllerFactory.create(
            infra.pgService(),
            shared.organizationService(),
            shared.itemService(),
            urnGenerator,
            infra.dataBrokerService(),
            config.getString("appIdRevokeExchange", "revoked-appid"),
            shared.userService()));

    controllers.add(
        AppTokenControllerFactory.create(
            infra.pgService(),
            shared.keycloakUserService(),
            shared.organizationService(),
            shared.userService(),
            shared.itemService(),
            urnGenerator,
            config,
            vertx,
            infra.dataBrokerService()));

    // Summary / dashboard
    controllers.add(SummaryControllerFactory.create(infra.pgService(), urnGenerator));

    // Leaderboard
    controllers.add(LeaderboardControllerFactory.create(infra.pgService(), urnGenerator));

    // User interactions v2
    controllers.add(
        UserInteractionV2controllerFactory.create(
            infra.pgService(), shared.itemService(), shared.auditingHandler(), urnGenerator));

    // Request conversations
    controllers.add(ConversationControllerFactory.create(infra.pgService(), urnGenerator));

    return controllers;
  }
}
