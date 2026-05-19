package org.cdpg.dx.auth.factory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.aaa.appCredentials.dao.AppConstraintsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.AppCredentialsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.impl.AppConstraintsDAOImpl;
import org.cdpg.dx.aaa.appCredentials.dao.impl.AppCredentialsDAOImpl;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.appCredentials.service.impl.AppCredentialsServiceImpl;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.auth.authentication.client.JwksResolver;
import org.cdpg.dx.auth.authentication.handler.AuthenticationHandler;
import org.cdpg.dx.auth.authentication.resolver.AppCredentialsResolverImpl;
import org.cdpg.dx.auth.authentication.resolver.DelegationResolverImpl;
import org.cdpg.dx.auth.authentication.resolver.JwtResolverImpl;
import org.cdpg.dx.common.config.ServiceProxyAddressConstants;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

/**
 * Builds a fully-wired {@link AuthenticationHandler} backed by in-process controlplane services —
 * no gRPC round-trips. Intended for {@code AbstractApiServerVerticle#getAuthV2Handler()} overrides
 * in controlplane verticles.
 */
public final class LocalAuthFactory {

  private LocalAuthFactory() {}

  public static AuthenticationHandler buildPair(
      Vertx vertx, JsonObject config, JwksResolver jwksResolver) {
    PostgresService postgresService =
        PostgresService.createProxy(vertx, ServiceProxyAddressConstants.POSTGRES_SERVICE_ADDRESS);
    DataBrokerService dataBrokerService =
        DataBrokerService.createProxy(
            vertx, ServiceProxyAddressConstants.DATA_BROKER_SERVICE_ADDRESS);

    AppCredentialsDAO appCredentialsDAO = new AppCredentialsDAOImpl(postgresService);
    AppConstraintsDAO appConstraintsDAO = new AppConstraintsDAOImpl(postgresService);
    AppCredentialsService appCredentialsService =
        new AppCredentialsServiceImpl(
            null,
            appCredentialsDAO,
            appConstraintsDAO,
            dataBrokerService,
            config.getString("appIdRevokeExchange", "revoked-appid"),
            null);

    DelegationService delegationService =
        DelegationService.createProxy(
            vertx, ServiceProxyAddressConstants.DELEGATION_SERVICE_ADDRESS);

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);

    JwtResolverImpl jwtResolver = new JwtResolverImpl(jwksResolver);
    DelegationResolverImpl delegationResolver = new DelegationResolverImpl(delegationService);
    AppCredentialsResolverImpl appResolver =
        new AppCredentialsResolverImpl(appCredentialsService, keycloakUserService);

    AuthenticationHandler authentication =
        new AuthenticationHandler(jwtResolver, delegationResolver, appResolver);
    return authentication;
  }

  public static Handler<RoutingContext> build(
      Vertx vertx, JsonObject config, JwksResolver jwksResolver) {
    return buildPair(vertx, config, jwksResolver);
  }
}
