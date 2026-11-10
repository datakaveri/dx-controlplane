package org.cdpg.dx.aaa.clientSecret.factory;

import io.vertx.core.Vertx;
import org.cdpg.dx.aaa.clientSecret.controller.ClientController;
import org.cdpg.dx.aaa.clientSecret.dao.ClientcredetialDao;
import org.cdpg.dx.aaa.clientSecret.dao.impl.ClientcredetialDaoImpl;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialService;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ClientControllerFactory {

  public static ClientController create(PostgresService pgService, URNGenerator urnGenerator) {

    ClientcredetialDao clientcredetialDao = new ClientcredetialDaoImpl(pgService);
    ClientcredetialService clientcredetialService =
      new ClientcredetialServiceImpl(clientcredetialDao);

    return new ClientController(clientcredetialService,urnGenerator);
  }
}
