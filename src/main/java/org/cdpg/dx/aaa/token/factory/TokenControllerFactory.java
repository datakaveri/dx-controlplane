package org.cdpg.dx.aaa.token.factory;

import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.client.WebClient;
import org.cdpg.dx.aaa.clientSecret.dao.ClientcredetialDao;
import org.cdpg.dx.aaa.clientSecret.dao.impl.ClientcredetialDaoImpl;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialService;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialServiceImpl;
import org.cdpg.dx.aaa.delegation.DelegationAccessEvaluator;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.token.controller.TokenController;
import org.cdpg.dx.aaa.token.service.TokenService;
import org.cdpg.dx.aaa.token.service.impl.TokenServiceImpl;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class TokenControllerFactory {

  public static TokenController create(
      PostgresService pgService,
      ElasticsearchService elasticsearchService,
      JsonObject config,
      Vertx vertx,
      WebClient webClient,
      PolicyDao policyDao,
      DelegationService delegationService,
      URNGenerator urnGenerator) {

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);
    ClientcredetialDao clientcredetialDao = new ClientcredetialDaoImpl(pgService);
    ClientcredetialService clientcredetialService =
        new ClientcredetialServiceImpl(clientcredetialDao);

    String keystorePath = config.getString("keystorePath");
    String keystorePassword = config.getString("keystorePassword");
    int tokenExpirationMinutes = config.getInteger("tokenExpirationMinutes", 60);
    String isssuer = config.getString("cosDomain", "");

    JWTAuth provider = jwtInitConfig(keystorePath, keystorePassword, vertx);

    ItemService itemService =
        new ItemServiceImpl(
            elasticsearchService,
            keycloakUserService,
            policyDao,
            webClient,
            config.getString(DOC_INDEX),
            config.getString(APD_URL));

    DelegationAccessEvaluator delegationAccessEvaluator = new DelegationAccessEvaluator(delegationService,itemService,keycloakUserService);

    TokenService tokenService =
        new TokenServiceImpl(
            provider,
            keycloakUserService,
            clientcredetialService,
            itemService,
            isssuer,
            tokenExpirationMinutes,
            delegationService,
            delegationAccessEvaluator,
            vertx);

    return new TokenController(tokenService, urnGenerator);
  }

  public static JWTAuth jwtInitConfig(String keystorePath, String keystorePassword, Vertx vertx) {
    JWTAuthOptions config = new JWTAuthOptions();
    config.setKeyStore(new KeyStoreOptions().setPath(keystorePath).setPassword(keystorePassword));

    return JWTAuth.create(vertx, config);
  }
}
