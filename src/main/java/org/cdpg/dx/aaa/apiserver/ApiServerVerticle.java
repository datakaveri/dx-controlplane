package org.cdpg.dx.aaa.apiserver;

import static org.cdpg.dx.aaa.common.Constants.IS_CENTRAL_CATALOGUE_ENABLED;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.KeyStoreOptions;
import java.util.List;
import java.util.function.Supplier;
import org.cdpg.dx.aaa.publicKey.service.PublicService;
import org.cdpg.dx.aaa.publicKey.service.impl.PublicServiceImpl;
import org.cdpg.dx.apiserver.AbstractApiServerVerticle;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auth.factory.LocalAuthFactory;
import org.cdpg.dx.auth.authentication.handler.AuthenticationHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.metrics.KeystoreExpiryMetrics;

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
    return "urn:dx:controlPlane:";
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
    KeystoreExpiryMetrics.bindToDefaultRegistry(vertx, keyStorePath, keyStorePassword);
    PublicService publicService = new PublicServiceImpl(keyStorePath, keyStorePassword, vertx);
    return () -> Future.succeededFuture(publicService.generateJwks());
  }

  @Override
  protected AuthenticationHandler getAuthV2Handler() {
    return LocalAuthFactory.buildPair(vertx, config(), jwksResolver);
  }

  @Override
  protected void configureSsl(HttpServerOptions serverOptions) {
    boolean isSsl = config().getBoolean("ssl", false);
    if (isSsl) {
      String keystore = config().getString("keystore");
      String keystorePassword = config().getString("keystorePassword");
      serverOptions
          .setSsl(true)
          .setKeyCertOptions(new KeyStoreOptions().setPath(keystore).setPassword(keystorePassword));
    }
  }
}
