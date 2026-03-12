package org.cdpg.dx.databroker;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.*;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.serviceproxy.ServiceBinder;
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
import org.cdpg.dx.databroker.RabbitClient;
import org.cdpg.dx.databroker.RabbitWebClient;
import org.cdpg.dx.databroker.listeners.AuditMessageConsumer;
import org.cdpg.dx.databroker.listeners.EmailMessageConsumer;
import org.cdpg.dx.databroker.listeners.LeaderboardConsumer;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.databroker.service.DataBrokerServiceImpl;
import org.cdpg.dx.databroker.util.Vhosts;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class DataBrokerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LogManager.getLogger(DataBrokerVerticle.class);
  private DataBrokerService dataBrokerService;
  private RabbitMQOptions rabbitMQOptions;
  private String dataBrokerIp;
  private int dataBrokerPort;
  private int dataBrokerManagementPort;
  private String dataBrokerUserName;
  private String dataBrokerPassword;
  private int connectionTimeout;
  private int requestedHeartbeat;
  private int handshakeTimeout;
  private int requestedChannelMax;
  private int networkRecoveryInterval;
  private WebClientOptions webConfig;
  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;
  private RabbitClient rabbitClient;
  private RabbitWebClient rabbitWebClient;
  private RabbitMQClient iudxRabbitMqClient;
  private RabbitMQClient iudxInternalRabbitMqClient;
  private int amqpPort;
  private String amqpUrl;
  private AuditMessageConsumer auditConsumer;
  private EmailMessageConsumer emailMessageConsumer;

  @Override
  public void start() throws Exception {

    /* Read the configuration and set the rabbitMQ server properties. */
    dataBrokerIp = config().getString("dataBrokerIP");
    dataBrokerPort = config().getInteger("dataBrokerPort");
    dataBrokerManagementPort = config().getInteger("dataBrokerManagementPort");
    dataBrokerUserName = config().getString("dataBrokerUserName");
    dataBrokerPassword = config().getString("dataBrokerPassword");
    connectionTimeout = config().getInteger("connectionTimeout");
    requestedHeartbeat = config().getInteger("requestedHeartbeat");
    handshakeTimeout = config().getInteger("handshakeTimeout");
    requestedChannelMax = config().getInteger("requestedChannelMax");
    networkRecoveryInterval = config().getInteger("networkRecoveryInterval");
    amqpUrl = config().getString("brokerAmqpIp");
    amqpPort = config().getInteger("brokerAmqpPort");

    /* Configure the RabbitMQ Data Broker client with input from config files. */
    rabbitMQOptions = new RabbitMQOptions();
    rabbitMQOptions.setUser(dataBrokerUserName);
    rabbitMQOptions.setPassword(dataBrokerPassword);
    rabbitMQOptions.setHost(dataBrokerIp);
    rabbitMQOptions.setPort(dataBrokerPort);
    rabbitMQOptions.setConnectionTimeout(connectionTimeout);
    rabbitMQOptions.setRequestedHeartbeat(requestedHeartbeat);
    rabbitMQOptions.setHandshakeTimeout(handshakeTimeout);
    rabbitMQOptions.setRequestedChannelMax(requestedChannelMax);
    rabbitMQOptions.setNetworkRecoveryInterval(networkRecoveryInterval);
    rabbitMQOptions.setAutomaticRecoveryEnabled(true);

    String externalVhost = config().getString(Vhosts.IUDX_EXTERNAL.value);

    RabbitMQOptions iudxConfig = new RabbitMQOptions(rabbitMQOptions);
    String prodVhost = config().getString(Vhosts.IUDX_PROD.value);

    RabbitMQOptions iudxInternalConfig = new RabbitMQOptions(rabbitMQOptions);
    String iudxInternalVhost = config().getString(Vhosts.IUDX_INTERNAL.value);

    iudxConfig.setVirtualHost(prodVhost);
    iudxInternalConfig.setVirtualHost(iudxInternalVhost);

    webConfig = new WebClientOptions();
    webConfig.setKeepAlive(true);
    webConfig.setConnectTimeout(86400000);
    webConfig.setDefaultHost(dataBrokerIp);
    webConfig.setDefaultPort(dataBrokerManagementPort);
    webConfig.setKeepAliveTimeout(86400000);

    /* Create a RabbitMQ Clinet with the configuration and vertx cluster instance. */
    RabbitMQClient.create(vertx, rabbitMQOptions);

    /* Create a Vertx Web Client with the configuration and vertx cluster instance. */
    WebClient.create(vertx, webConfig);

    /* Create a Json Object for properties */
    JsonObject propObj = new JsonObject();

    propObj.put("username", dataBrokerUserName);
    propObj.put("password", dataBrokerPassword);

    /* Call the databroker constructor with the RabbitMQ client. */
    rabbitWebClient = new RabbitWebClient(vertx, webConfig, propObj);
    iudxRabbitMqClient = RabbitMQClient.create(vertx, iudxConfig);
    iudxInternalRabbitMqClient = RabbitMQClient.create(vertx, iudxInternalConfig);
    rabbitClient =
        new RabbitClient(vertx, rabbitWebClient, iudxInternalRabbitMqClient, iudxRabbitMqClient);
    binder = new ServiceBinder(vertx);

    PostgresService postgresService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    /*ImmudbActivityService immudbActivityService = new ImmudbActivityServiceImpl(immudbService);*/

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config());

    ElasticsearchService esService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);
    PostgresService pgService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    String docIndex = config().getString("docIndex");
    ItemService itemService =
        new ItemServiceImpl(esService, null, null, null, null, docIndex, null);
    AssetEnrichmentService assetEnrichmentService = new AssetEnrichmentService(itemService);
    UserEnrichmentService userEnrichmentService = new UserEnrichmentService(keycloakUserService);
    AuditEnrichmentService auditEnrichmentService =
        new AuditEnrichmentService(assetEnrichmentService, userEnrichmentService);

    UserActivityLogDao userActivityLogDao = new UserActivityLogDaoImpl(pgService);
    UserActivityAuditLogService userActivityAuditLogService =
        new UserActivityAuditLogServiceImpl(userActivityLogDao);

    String auditQueue = config().getString("auditingQueue");

    auditConsumer =
        new AuditMessageConsumer(
            iudxInternalRabbitMqClient,
            auditQueue,
            auditEnrichmentService,
            userActivityAuditLogService,
            itemService,
            true);
    auditConsumer.start();

    /*immudbConsumer = new ImmudbConsumer(iudxInternalRabbitMqClient, immudbActivityService);*/

    LeaderboardDaoV2 leaderboardDaoV2 = new LeaderboardDaoImplV2(pgService);

    LeaderboardConsumer leaderboardConsumer =
        new LeaderboardConsumer(
            iudxInternalRabbitMqClient,
            new LeaderboardEnrichmentService(itemService),
            new LeaderboardWriterService(leaderboardDaoV2),
            config().getString("leaderboardQueue", "leaderboard"));

    leaderboardConsumer.start();

    EmailService emailService = EmailService.createProxy(vertx, EMAIL_SERVICE_ADDRESS);
    String emailQueue = config().getString("emailQueue", "email-notification");
    emailMessageConsumer =
        new EmailMessageConsumer(iudxInternalRabbitMqClient, emailQueue, emailService);
    emailMessageConsumer.start();
    /*immudbConsumer.start();*/

    dataBrokerService =
        new DataBrokerServiceImpl(
            rabbitClient, amqpUrl, amqpPort, iudxInternalVhost, prodVhost, externalVhost);

    /* Publish the Data Broker service with the Event Bus against an address. */

    consumer =
        binder
            .setAddress(DATA_BROKER_SERVICE_ADDRESS)
            .register(DataBrokerService.class, dataBrokerService);
  }

  @Override
  public void stop() throws Exception {
    binder.unregister(consumer);
  }
}
