package org.cdpg.dx.acl.apiserver;

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
import org.cdpg.dx.auth.v2.factory.AuthHandlersV2;
import org.cdpg.dx.auth.v2.factory.LocalAuthV2Factory;
import org.cdpg.dx.common.URNGenerator;

public class ApdApiServerVerticle extends AbstractApiServerVerticle {

  private AuthHandlersV2 authV2Pair;

  @Override
  protected String getOpenApiSpecPath(JsonObject config) {
    return "docs/acl-openapi.yaml";
  }

  @Override
  protected int getDefaultPort() {
    return 8444;
  }

  @Override
  protected String getDefaultUrnPrefix() {
    return "urn:dx:apdServerPanel:";
  }

  @Override
  protected String getBaseUrlConfigKey() {
    return "apdURL";
  }

  @Override
  protected String getDefaultSupportEmail() {
    return "support@datakaveri.org";
  }

  @Override
  protected List<ApiController> createControllers(
      Vertx vertx, JsonObject config, URNGenerator urnGenerator) {
    this.authV2Pair = LocalAuthV2Factory.buildPair(vertx, config);
    return ControllerFactory.createControllers(vertx, config, urnGenerator, authV2Pair);
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
    return authV2Pair.authentication();
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
