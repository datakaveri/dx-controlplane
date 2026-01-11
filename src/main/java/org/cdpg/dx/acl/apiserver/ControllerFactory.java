package org.cdpg.dx.acl.apiserver;

import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.aaa.common.Constants.VOC_CONTEXT;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.*;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.ActivityReport.controller.ActivityReportController;
import org.cdpg.dx.aaa.ActivityReport.factory.ActivityReportControllerFactory;
import org.cdpg.dx.aaa.activity.controller.ActivityController;
import org.cdpg.dx.aaa.activity.factory.ActivityControllerFactory;
import org.cdpg.dx.aaa.activity.factory.ActivityFactory;
import org.cdpg.dx.aaa.activity.service.ActivityLogService;
import org.cdpg.dx.acl.accessReport.controller.AccessReportController;
import org.cdpg.dx.acl.accessReport.factory.AccessReportFactory;
import org.cdpg.dx.acl.accessRequest.controller.AccessRequestController;
import org.cdpg.dx.acl.accessRequest.factory.AccessRequestFactory;
import org.cdpg.dx.acl.policy.controller.PolicyController;
import org.cdpg.dx.acl.policy.factory.PolicyFactory;
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

  public static List<ApdApiController> createControllers(
      Vertx vertx, JsonObject config, URNGenerator urnGenerator) {

    final String docIndex = config.getString(DOC_INDEX);
    final String vocContext = config.getString(VOC_CONTEXT);

    WebClient webClient = WebClient.create(vertx);

    PostgresService pgService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    DataBrokerService dataBrokerService =
        DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    EmailService emailService = EmailService.createProxy(vertx, EMAIL_SERVICE_ADDRESS);
    ElasticsearchService esService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);

    String auditingExchange = config.getString("auditingExchange");
    String routingKey = config.getString("auditingRoutingKey");
    boolean isRemoteAudit = config.getBoolean("isRemoteAudit", false);
    String emailExchange = config.getString("emailExchange", "Email");
    String emailRoutingKey = config.getString("emailRoutingKey", "##");
    // Activity Controller
    ActivityController activityController =
        ActivityControllerFactory.create(pgService, urnGenerator);
    ActivityReportController activityReportController =
        ActivityReportControllerFactory.create(pgService, vertx);
    ActivityLogService activityLogService = ActivityFactory.getActivityService();

    AuditingHandler auditingHandler =
        new AuditingHandler(
            dataBrokerService, activityLogService, auditingExchange, routingKey, isRemoteAudit);

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);

    AccessRequestController accessRequestController =
        AccessRequestFactory.createAccessRequestController(
            pgService,
            esService,
            dataBrokerService,
            keycloakUserService,
            auditingHandler,
            config,
            urnGenerator,
            webClient,
            emailExchange,
            emailRoutingKey);

    AccessReportController accessReportController = AccessReportFactory.create(pgService, vertx);
    PolicyController policyController =
        PolicyFactory.createPolicyController(
            pgService,
            esService,
            keycloakUserService,
            auditingHandler,
            urnGenerator,
            webClient,
            config);
    return List.of(accessRequestController, accessReportController, policyController);
  }
}
