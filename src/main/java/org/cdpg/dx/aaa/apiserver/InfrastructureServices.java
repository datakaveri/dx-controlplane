package org.cdpg.dx.aaa.apiserver;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.CENTRAL_ELASTIC_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.DATA_BROKER_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.ELASTIC_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.EMAIL_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.POSTGRES_SERVICE_ADDRESS;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.email.service.EmailService;

/**
 * Groups the low-level infrastructure service proxies that are created once during application
 * startup and shared across all domain controller factories.
 *
 * @param pgService PostgreSQL service proxy
 * @param esService Elasticsearch service proxy
 * @param centralEsService Central Elasticsearch service proxy (null when central catalogue is
 *     disabled)
 * @param dataBrokerService RabbitMQ data broker service proxy
 * @param emailService email service proxy
 * @param webClient Vert.x WebClient for external HTTP calls
 */
public record InfrastructureServices(
    PostgresService pgService,
    ElasticsearchService esService,
    ElasticsearchService centralEsService,
    DataBrokerService dataBrokerService,
    EmailService emailService,
    WebClient webClient) {

  /** Create all infrastructure service proxies from the Vert.x instance. */
  public static InfrastructureServices create(Vertx vertx, boolean isCentralCatEnabled) {
    ElasticsearchService centralEsService =
        isCentralCatEnabled
            ? ElasticsearchService.createProxy(vertx, CENTRAL_ELASTIC_SERVICE_ADDRESS)
            : null;
    return new InfrastructureServices(
        PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS),
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS),
        centralEsService,
        DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS),
        EmailService.createProxy(vertx, EMAIL_SERVICE_ADDRESS),
        WebClient.create(vertx));
  }
}
