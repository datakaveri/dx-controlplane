package org.cdpg.dx.aaa.grpc;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.DATA_BROKER_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.DELEGATION_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.ELASTIC_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.POSTGRES_SERVICE_ADDRESS;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.WebClient;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.appCredentials.dao.AppConstraintsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.AppCredentialsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.impl.AppConstraintsDAOImpl;
import org.cdpg.dx.aaa.appCredentials.dao.impl.AppCredentialsDAOImpl;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.appCredentials.service.impl.AppCredentialsServiceImpl;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.grpc.auth.JwksCache;
import org.cdpg.dx.aaa.grpc.auth.ServiceAuthInterceptor;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class GrpcServerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LogManager.getLogger(GrpcServerVerticle.class);
  private Server grpcServer;

  @Override
  public void start(Promise<Void> startPromise) {
    int grpcPort = config().getInteger("grpcPort", 9090);
    String docIndex = config().getString("docIndex", "iudx-docs");
    String apdUrl = config().getString("apdURL", "");

    PostgresService postgresService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    ElasticsearchService elasticsearchService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);
    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config());
    WebClient webClient = WebClient.create(vertx);
    PolicyDao policyDao = new PolicyDaoImpl(postgresService);

    ItemService itemService =
        new ItemServiceImpl(
            elasticsearchService,
            keycloakUserService,
            postgresService,
            policyDao,
            webClient,
            docIndex,
            apdUrl);

    AppCredentialsDAO appCredentialsDAO = new AppCredentialsDAOImpl(postgresService);
    AppConstraintsDAO appConstraintsDAO = new AppConstraintsDAOImpl(postgresService);
    DataBrokerService dataBrokerService =
        DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    AppCredentialsService appCredentialsService =
        new AppCredentialsServiceImpl(
            null,
            appCredentialsDAO,
            appConstraintsDAO,
            dataBrokerService,
            config().getString("appIdRevokeExchange", "revoked-appid"),
            null);

    DelegationService delegationService =
        DelegationService.createProxy(vertx, DELEGATION_SERVICE_ADDRESS);

    AppIdVerificationGrpcService grpcService =
        new AppIdVerificationGrpcService(
            vertx, appCredentialsService, itemService, delegationService, keycloakUserService);

    CatItemGrpcService catItemGrpcService =
        new CatItemGrpcService(vertx, itemService, keycloakUserService);

    // JWKS URL — prefer explicit override, fall back to derived URL.
    // VM Keycloak uses legacy /auth/ prefix; local Keycloak 26 start-dev does not.
    // Always set keycloakJwksUrl explicitly in config to avoid ambiguity.
    String keycloakUrl = config().getString("keycloakUrl", "http://localhost:8180");
    String keycloakRealm = config().getString("keycloakRealm", "iudx-v2");
    String jwksUrl =
        config()
            .getString(
                "keycloakJwksUrl",
                keycloakUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/certs");

    JsonArray allowedClientsArray =
        config()
            .getJsonArray("grpcAllowedServiceClients", new JsonArray().add("svc-dx-dataplane"));
    Set<String> allowedServiceClients =
        allowedClientsArray.stream().map(Object::toString).collect(Collectors.toSet());

    JwksCache jwksCache = new JwksCache(jwksUrl, 600);
    ServiceAuthInterceptor authInterceptor =
        new ServiceAuthInterceptor(jwksCache, allowedServiceClients);

    LOGGER.info(
        "gRPC ServiceAuthInterceptor configured: jwksUrl={} allowedClients={}",
        jwksUrl,
        allowedServiceClients);

    try {
      grpcServer =
          ServerBuilder.forPort(grpcPort)
              .addService(ServerInterceptors.intercept(grpcService, authInterceptor))
              .addService(ServerInterceptors.intercept(catItemGrpcService, authInterceptor))
              .build()
              .start();
      LOGGER.info("AppId + CatItem gRPC server started on port {}", grpcPort);
      startPromise.complete();
    } catch (IOException e) {
      LOGGER.error("Failed to start AppId gRPC server", e);
      startPromise.fail(e);
    }
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    if (grpcServer != null) {
      grpcServer.shutdown();
      try {
        if (!grpcServer.awaitTermination(5, TimeUnit.SECONDS)) {
          grpcServer.shutdownNow();
        }
      } catch (InterruptedException e) {
        grpcServer.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    stopPromise.complete();
  }
}
