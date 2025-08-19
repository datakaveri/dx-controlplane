package org.cdpg.dx.aaa.token.factory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.cdpg.dx.aaa.clientSecret.dao.ClientcredetialDao;
import org.cdpg.dx.aaa.clientSecret.dao.impl.ClientcredetialDaoImpl;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialService;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialServiceImpl;
import org.cdpg.dx.aaa.token.controller.TokenController;
import org.cdpg.dx.aaa.token.service.TokenService;
import org.cdpg.dx.aaa.token.service.impl.TokenServiceImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class TokenControllerFactory {

  public static TokenController create(PostgresService pgService, JsonObject config, Vertx vertx) {

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);
    ClientcredetialDao clientcredetialDao = new ClientcredetialDaoImpl(pgService);
    ClientcredetialService clientcredetialService =
        new ClientcredetialServiceImpl(clientcredetialDao);

    String keystorePath = config.getString("keystorePath");
    String keystorePassword = config.getString("keystorePassword");
    int tokenExpirationMinutes = config.getInteger("tokenExpirationMinutes", 60);
    String isssuer = config.getString("cosDomain", "");

    JWTAuth provider = jwtInitConfig(keystorePath, keystorePassword, vertx);

    TokenService tokenService =
        new TokenServiceImpl(
            provider,
            keycloakUserService,
            clientcredetialService,
            keystorePath,
            keystorePassword,
            tokenExpirationMinutes,
            isssuer,
            vertx);

    return new TokenController(tokenService);
  }

  public static JWTAuth jwtInitConfig(String keystorePath, String keystorePassword, Vertx vertx) {
    JWTAuthOptions config = new JWTAuthOptions();
    config.setKeyStore(new KeyStoreOptions().setPath(keystorePath).setPassword(keystorePassword));

    return JWTAuth.create(vertx, config);
  }
}
