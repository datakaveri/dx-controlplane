package org.cdpg.dx.aaa.asset.factory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.asset.dao.AssetRequestDAO;
import org.cdpg.dx.aaa.asset.dao.impl.AssetRequestDAOImpl;
import org.cdpg.dx.aaa.asset.handler.AssetHandler;
import org.cdpg.dx.aaa.asset.service.AssetService;
import org.cdpg.dx.aaa.asset.service.AssetServiceImpl;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.kyc.dao.KYCTransactionDAO;
import org.cdpg.dx.aaa.kyc.dao.impl.KYCTransactionDAOImpl;
import org.cdpg.dx.aaa.kyc.handler.KYCHandler;
import org.cdpg.dx.aaa.kyc.service.KYCService;
import org.cdpg.dx.aaa.kyc.service.KYCServiceImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class AssetFactory {
  private static final Logger LOGGER = LogManager.getLogger(AssetFactory.class);

  private AssetFactory() {}

  public static AssetHandler createHandler( PostgresService postgresService, JsonObject config, EmailComposer emailComposer){

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);
    AssetRequestDAO assetRequestDAO = new AssetRequestDAOImpl(postgresService);

    AssetService assetService = new AssetServiceImpl(assetRequestDAO,keycloakUserService,config);

    return new AssetHandler(assetService, emailComposer, keycloakUserService);
  }

}
