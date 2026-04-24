package org.cdpg.dx.aaa.apiserver;

import static org.cdpg.dx.aaa.common.Constants.IS_CENTRAL_CATALOGUE_ENABLED;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.function.Supplier;
import org.cdpg.dx.aaa.publicKey.service.PublicService;
import org.cdpg.dx.aaa.publicKey.service.impl.PublicServiceImpl;
import org.cdpg.dx.apiserver.AbstractApiServerVerticle;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auth.v2.factory.LocalAuthV2Factory;
import org.cdpg.dx.common.URNGenerator;

public class ApiServerVerticle extends AbstractApiServerVerticle {

  @Override
  protected String getOpenApiSpecPath(JsonObject config) {
    boolean isCentral = config.getBoolean(IS_CENTRAL_CATALOGUE_ENABLED, false);
    return isCentral ? "docs/central-openapi.yaml" : "docs/openapi.yaml";
  }

  @Override
  protected int getDefaultPort() {
    return 8443;
  }

  @Override
  protected String getDefaultUrnPrefix() {
    return "urn:dx:controlPanel:";
  }

  @Override
  protected List<ApiController> createControllers(
      Vertx vertx, JsonObject config, URNGenerator urnGenerator) {
    return ControllerFactory.createControllers(vertx, config, urnGenerator);
  }

  @Override
  protected String getDefaultSupportEmail() {
    return "support@datakaveri.org";
  }

  @Override
  protected Supplier<Future<JsonObject>> getJwksInternalProvider() {
    String keyStorePath = config().getString("keystorePath");
    String keyStorePassword = config().getString("keystorePassword");
    PublicService publicService = new PublicServiceImpl(keyStorePath, keyStorePassword, vertx);
    return () -> Future.succeededFuture(publicService.generateJwks());
  }

  @Override
  protected Handler<RoutingContext> getAuthV2Handler() {
    return LocalAuthV2Factory.build(vertx, config());
  }

  @Override
  protected void configureSsl(HttpServerOptions serverOptions) {
    boolean isSsl = config().getBoolean("ssl", false);
    if (isSsl) {
      String keystore = config().getString("keystore");
      String keystorePassword = config().getString("keystorePassword");
      serverOptions
          .setSsl(true)
          .setKeyCertOptions(
              new KeyStoreOptions().setPath(keystore).setPassword(keystorePassword));
    }
  }
}
