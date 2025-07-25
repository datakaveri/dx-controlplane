package org.cdpg.dx.aaa.kyc.factory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.cache.service.CacheService;
import org.cdpg.dx.aaa.cache.service.CacheServiceImpl;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.credit.service.CreditServiceImpl;
import org.cdpg.dx.aaa.kyc.dao.KYCTransactionDAO;
import org.cdpg.dx.aaa.kyc.dao.impl.KYCTransactionDAOImpl;
import org.cdpg.dx.aaa.kyc.handler.KYCHandler;
import org.cdpg.dx.aaa.kyc.service.KYCService;
import org.cdpg.dx.aaa.kyc.service.KYCServiceImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class KYCFactory {
  private static final Logger LOGGER = LogManager.getLogger(KYCFactory.class);

  private KYCFactory() {}

  public static KYCHandler createHandler(Vertx vertx, JsonObject config, CreditService creditService, PostgresService postgresService) {

    WebClient webClient = WebClient.create(vertx);
    KYCTransactionDAO kycTransactionDAO = new KYCTransactionDAOImpl(postgresService);
    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);

    KYCService kycService = new KYCServiceImpl(webClient, kycTransactionDAO, keycloakUserService, config);
    return new KYCHandler(kycService, keycloakUserService, creditService);
  }
}
