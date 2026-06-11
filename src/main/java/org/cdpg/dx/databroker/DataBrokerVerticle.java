package org.cdpg.dx.databroker;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.*;

import io.vertx.rabbitmq.RabbitMQClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.activity.dao.UserActivityLogDao;
import org.cdpg.dx.aaa.activity.dao.impl.UserActivityLogDaoImpl;
import org.cdpg.dx.aaa.activity.service.UserActivityAuditLogService;
import org.cdpg.dx.aaa.activity.service.impl.UserActivityAuditLogServiceImpl;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.leaderboard.dao.LeaderboardDaoV2;
import org.cdpg.dx.aaa.leaderboard.dao.impl.LeaderboardDaoImplV2;
import org.cdpg.dx.aaa.leaderboard.enrichment.LeaderboardEnrichmentService;
import org.cdpg.dx.aaa.leaderboard.writer.LeaderboardWriterService;
import org.cdpg.dx.auditing.v2.enrichment.AssetEnrichmentService;
import org.cdpg.dx.auditing.v2.enrichment.AuditEnrichmentService;
import org.cdpg.dx.auditing.v2.enrichment.UserEnrichmentService;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.listeners.AuditMessageConsumer;
import org.cdpg.dx.databroker.listeners.GatewayLogConsumer;
import org.cdpg.dx.databroker.listeners.EmailMessageConsumer;
import org.cdpg.dx.databroker.listeners.LeaderboardConsumer;
import org.cdpg.dx.databroker.verticle.BaseDataBrokerVerticle;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

/**
 * Controlplane DataBrokerVerticle — sets up audit, email, and leaderboard consumers.
 */
public class DataBrokerVerticle extends BaseDataBrokerVerticle {

  private static final Logger LOGGER = LogManager.getLogger(DataBrokerVerticle.class);

  @Override
  protected void onBrokerReady(
      RabbitMQClient internalClient, RabbitMQClient prodClient, RabbitClient rabbitClient) {

    PostgresService pgService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    ElasticsearchService esService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);
    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config());

    // Item service for enrichment
    String docIndex = config().getString("docIndex");
    ItemService itemService =
        new ItemServiceImpl(esService, null, null, null, null, docIndex, null);

    // Audit consumer
    AssetEnrichmentService assetEnrichmentService = new AssetEnrichmentService(itemService);
    UserEnrichmentService userEnrichmentService = new UserEnrichmentService(keycloakUserService);
    AuditEnrichmentService auditEnrichmentService =
        new AuditEnrichmentService(assetEnrichmentService, userEnrichmentService);

    UserActivityLogDao userActivityLogDao = new UserActivityLogDaoImpl(pgService);
    UserActivityAuditLogService userActivityAuditLogService =
        new UserActivityAuditLogServiceImpl(userActivityLogDao);

    String auditQueue = config().getString("auditingQueue");
    AuditMessageConsumer auditConsumer =
        new AuditMessageConsumer(
            internalClient,
            auditQueue,
            auditEnrichmentService,
            userActivityAuditLogService,
            itemService,
            true);
    auditConsumer.start();

    // Leaderboard consumer
    LeaderboardDaoV2 leaderboardDaoV2 = new LeaderboardDaoImplV2(pgService);
    LeaderboardConsumer leaderboardConsumer =
        new LeaderboardConsumer(
            internalClient,
            new LeaderboardEnrichmentService(itemService),
            new LeaderboardWriterService(leaderboardDaoV2),
            config().getString("leaderboardQueue", "leaderboard"));
    leaderboardConsumer.start();

    // Gateway security-log consumer (events from dx-gateway-go)
    String gatewayLogQueue = config().getString("gatewayLogQueue", "gateway-logs");
    GatewayLogConsumer gatewayLogConsumer =
        new GatewayLogConsumer(internalClient, gatewayLogQueue, pgService);
    gatewayLogConsumer.start();

    // Email consumer
    EmailService emailService = EmailService.createProxy(vertx, EMAIL_SERVICE_ADDRESS);
    String emailQueue = config().getString("emailQueue", "email-notification");
    EmailMessageConsumer emailMessageConsumer =
        new EmailMessageConsumer(internalClient, emailQueue, emailService);
    emailMessageConsumer.start();

    LOGGER.info("DataBrokerVerticle: audit, leaderboard, and email consumers started");
  }
}
