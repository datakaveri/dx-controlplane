package org.cdpg.dx.aaa.delegation;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.serviceproxy.ServiceBinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.dao.DelegationDAOFactory;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.delegation.service.DelegationServiceImpl;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAOFactory;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.organization.service.OrganizationServiceImpl;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.catalogueService.client.CatalogueClient;
import org.cdpg.dx.catalogueService.service.CatalogueService;
import org.cdpg.dx.catalogueService.service.CatalogueServiceImpl;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.Pool;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.*;


public class DelegationVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LogManager.getLogger(DelegationVerticle.class);

  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;

  @Override
  public void start(Promise<Void> startPromise) {
    try {
      // ------------------- DB Pool -------------------
//      Pool pool = /* get or create your shared SQL pool */;

      ElasticsearchService elasticsearchService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);

      PostgresService postgresService =
        PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);



      DelegationDAOFactory daoFactory = new DelegationDAOFactory(postgresService);
      OrganizationDAOFactory organizationDAOFactory = new OrganizationDAOFactory(postgresService);
      PolicyDao policyDao = new PolicyDaoImpl(postgresService);
      WebClient webClient = WebClient.create(vertx);

      String apdUrl = config().getString("apdURL");
      String docIndex = config().getString("docIndex");
      KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config());


      // ------------------- Dependent services -------------------


      ItemService itemService = new ItemServiceImpl(elasticsearchService,keycloakUserService,policyDao,webClient,docIndex,apdUrl); // direct instance
      OrganizationService organizationService = new OrganizationServiceImpl(organizationDAOFactory,keycloakUserService,itemService); // direct instance

      // ------------------- Delegation Service -------------------
      DelegationService delegationService = new DelegationServiceImpl(
        daoFactory,
        keycloakUserService,
        organizationService,
        itemService
      );

      // ------------------- Register on Event Bus -------------------
      binder = new ServiceBinder(vertx);
      consumer = binder.setAddress(DELEGATION_SERVICE_ADDRESS)
        .register(DelegationService.class, delegationService);

      startPromise.complete();
      LOGGER.info("DelegationVerticle started successfully");
    } catch (Exception e) {
      LOGGER.error("Failed to start DelegationVerticle", e);
      startPromise.fail(e);
    }
  }

  @Override
  public void stop() {
    if (binder != null && consumer != null) {
      binder.unregister(consumer);
    }
  }
}

