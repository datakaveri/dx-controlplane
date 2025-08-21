package org.cdpg.dx.aaa.publicKey.factory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.publicKey.controller.PublicController;
import org.cdpg.dx.aaa.publicKey.service.PublicService;
import org.cdpg.dx.aaa.publicKey.service.impl.PublicServiceImpl;

public class PublicKeycontrllerFactory {

  public static PublicController create(JsonObject config, Vertx vertx) {



    String keyStorePath = config.getString("keystorePath");
    String keyStorePassword = config.getString("keystorePassword");


    PublicService publicService = new PublicServiceImpl(keyStorePath, keyStorePassword, vertx);

    return new PublicController(publicService);
  }
}
