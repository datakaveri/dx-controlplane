package org.cdpg.dx.aaa.token.factory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import org.cdpg.dx.aaa.appCredentials.dao.AppConstraintsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.AppCredentialsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.impl.AppConstraintsDAOImpl;
import org.cdpg.dx.aaa.appCredentials.dao.impl.AppCredentialsDAOImpl;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.appCredentials.service.impl.AppCredentialsServiceImpl;
import org.cdpg.dx.aaa.delegation.DelegationValidator;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.token.controller.AppTokenController;
import org.cdpg.dx.aaa.token.controller.TokenController;
import org.cdpg.dx.aaa.token.service.AppTokenService;
import org.cdpg.dx.aaa.token.service.impl.AppTokenServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.jboss.resteasy.plugins.delegates.UriHeaderDelegate;

import static org.cdpg.dx.aaa.token.factory.TokenControllerFactory.jwtInitConfig;

public class AppTokenControllerFactory {

  public static AppTokenController create(
      PostgresService pgService,
      KeycloakUserService keycloakUserService,
      OrganizationService organizationService,
      ItemService itemService,
      URNGenerator urnGenerator,
      JsonObject config,
      Vertx vertx) {

    String keystorePath = config.getString("keystorePath");
    String keystorePassword = config.getString("keystorePassword");
    int tokenExpirationMinutes = config.getInteger("tokenExpirationMinutes", 60);
    String issuer = config.getString("cosDomain", "");
    JWTAuth provider = jwtInitConfig(keystorePath, keystorePassword, vertx);

    AppCredentialsDAO appCredentialsDAO = new AppCredentialsDAOImpl(pgService);
    AppConstraintsDAO appConstraintsDAO = new AppConstraintsDAOImpl(pgService);

    DelegationValidator delegationValidator = new DelegationValidator(organizationService,itemService);
    AppCredentialsService appCredentialsService = new AppCredentialsServiceImpl(delegationValidator,appCredentialsDAO,appConstraintsDAO);

    AppTokenService appTokenService =
        new AppTokenServiceImpl(
            provider,
            keycloakUserService,
            appCredentialsService,
            itemService,
            issuer,
            tokenExpirationMinutes,
            vertx);

    return new AppTokenController(appTokenService, urnGenerator);
  }
}
