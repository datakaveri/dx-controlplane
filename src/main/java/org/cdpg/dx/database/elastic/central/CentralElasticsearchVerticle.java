package org.cdpg.dx.database.elastic.central;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.CENTRAL_ELASTIC_SERVICE_ADDRESS;
import static org.cdpg.dx.database.elastic.util.Constants.*;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;
import org.cdpg.dx.database.elastic.ElasticClient;
import org.cdpg.dx.database.elastic.central.service.CentralElasticsearchService;
import org.cdpg.dx.database.elastic.central.service.CentralElasticsearchServiceImpl;

/**
 * The Central Elasticsearch Verticle.
 *
 * <h1>Central Elasticsearch Verticle</h1>
 *
 * <p>This verticle exposes a dedicated {@link CentralElasticsearchService} instance for the
 * Central Catalogue, using separate ES connection configurations and a distinct
 * event-bus address {@code CENTRAL_ELASTIC_SERVICE_ADDRESS}.
 *
 * @version 1.0
 * @since 2025-12-08
 */
public class CentralElasticsearchVerticle extends AbstractVerticle {

  private CentralElasticsearchService centralDatabase;
  private String centralDbIp;
  private String centralDbUser;
  private String centralDbPassword;
  private int centralDbPort;
  private ElasticClient centralClient;
  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;

  /**
   * This method is used to start the Verticle. It deploys a verticle in a cluster, registers the
   * service with the Event bus against an address, publishes the service with the service discovery
   * interface.
   *
   * @throws Exception which is a start-up exception.
   */
  @Override
  public void start() throws Exception {
    binder = new ServiceBinder(vertx);
    centralDbIp = config().getString(DATABASE_IP);
    centralDbPort = config().getInteger(DATABASE_PORT);
    centralDbUser = config().getString(DATABASE_UNAME);
    centralDbPassword = config().getString(DATABASE_PASSWD);

    // Create a new client for central catalogue ES
    centralClient =
        new ElasticClient(centralDbIp, centralDbPort, centralDbUser, centralDbPassword);

    centralDatabase = new CentralElasticsearchServiceImpl(centralClient);

    // Register central catalogue service on event bus
    consumer =
        binder
            .setAddress(CENTRAL_ELASTIC_SERVICE_ADDRESS)
            .register(CentralElasticsearchService.class, centralDatabase);
  }

  @Override
  public void stop() {
    binder.unregister(consumer);
  }
}
