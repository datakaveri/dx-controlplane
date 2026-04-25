package org.cdpg.dx.auth.v2.factory;

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
import org.cdpg.dx.auth.v2.handler.AuthenticationHandler;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.lookup.local.LocalAppCredentialLookup;
import org.cdpg.dx.auth.v2.lookup.local.LocalDelegationLookup;
import org.cdpg.dx.auth.v2.lookup.local.LocalUserLookup;
import org.cdpg.dx.auth.v2.registry.InMemoryRoleScopeRegistry;
import org.cdpg.dx.auth.v2.resolver.AppCredentialsResolver;
import org.cdpg.dx.auth.v2.resolver.DelegationResolver;
import org.cdpg.dx.auth.v2.resolver.JwtPrincipalResolver;
import org.cdpg.dx.common.config.ServiceProxyAddressConstants;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

/**
 * Builds a fully-wired v2 {@link AuthenticationHandler} backed by in-process controlplane
 * services — no gRPC round-trips. Intended for {@code AbstractApiServerVerticle#getAuthV2Handler()}
 * overrides in controlplane verticles.
 */
public final class LocalAuthV2Factory {

  private LocalAuthV2Factory() {}

  public static AuthHandlersV2 buildPair(Vertx vertx, JsonObject config) {
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
            config.getString("appIdRevokeExchange", "revoked-appid"));

    DelegationService delegationService =
        DelegationService.createProxy(
            vertx, ServiceProxyAddressConstants.DELEGATION_SERVICE_ADDRESS);

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);

    LocalAppCredentialLookup appLookup = new LocalAppCredentialLookup(appCredentialsService);
    LocalDelegationLookup delegationLookup = new LocalDelegationLookup(delegationService);
    LocalUserLookup userLookup = new LocalUserLookup(keycloakUserService);

    AuthenticationHandler authentication =
        new AuthenticationHandler(
            new JwtPrincipalResolver(),
            new DelegationResolver(delegationLookup, userLookup),
            new AppCredentialsResolver(appLookup, userLookup));
    AuthorizationHandler authorization = new AuthorizationHandler(new InMemoryRoleScopeRegistry());
    return new AuthHandlersV2(authentication, authorization);
  }

  public static Handler<RoutingContext> build(Vertx vertx, JsonObject config) {
    return buildPair(vertx, config).authentication();
  }
}