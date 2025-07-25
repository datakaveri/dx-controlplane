package org.cdpg.dx.catalogueService;


import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.CATALOGUE_SERVICE_ADDRESS;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.serviceproxy.ServiceBinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.catalogueService.client.CatalogueClient;
import org.cdpg.dx.catalogueService.service.CatalogueService;
import org.cdpg.dx.catalogueService.service.CatalogueServiceImpl;

public class CatalogueVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LogManager.getLogger(CatalogueVerticle.class);

  @Override
  public void start() {
    WebClientOptions clientOptions =
        new WebClientOptions().setSsl(true).setVerifyHost(false).setTrustAll(true);
    WebClient client = WebClient.create(vertx, clientOptions);

    String catHost = config().getString("catServerHost");
    Integer catPort = config().getInteger("catServerPort");

    CatalogueClient catalogueClient = new CatalogueClient(client, catPort, catHost);

    String apdUrl = config().getString("apdUrl");
    String dxCatalogueBasePath = config().getString("dxCatalogueBasePath");


    CatalogueService catalogueService = new CatalogueServiceImpl(catalogueClient, dxCatalogueBasePath, apdUrl);
    new ServiceBinder(vertx)
        .setAddress(CATALOGUE_SERVICE_ADDRESS)
        .register(CatalogueService.class, catalogueService);
  }

}