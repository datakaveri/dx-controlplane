package org.cdpg.dx.acl.apiserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.openapi.RouterBuilderOptions;
import io.vertx.serviceproxy.HelperUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.handler.KeycloakJwtAuthHandler;
import org.cdpg.dx.auth.authentication.handler.OptionalJwtAuthHandler;
import org.cdpg.dx.auth.authentication.provider.JwtAuthProvider;
import org.cdpg.dx.common.FailureHandler;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.config.URNConstants;
import org.cdpg.dx.common.util.BlockingExecutionUtil;

import java.util.List;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.*;
import static org.cdpg.dx.common.config.CorsUtil.allowedOrigins;

public class ApdApiServerVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LogManager.getLogger(ApdApiServerVerticle.class);
  private int port;
  private HttpServer server;
  private Router router;

  public static String errorResponse(HttpStatusCode code) {
    return new JsonObject()
        .put("type", code.getUrn())
        .put("title", code.getDescription())
        .put("detail", code.getDescription())
        .toString();
  }

  @Override
  public void start() {
    port = config().getInteger("httpPort", 8444);
    allowedOrigins = config().getJsonArray("corsAllowedOrigin").getList();

    // Register the module for default Vert.x ObjectMapper
    ObjectMapper mapper = DatabindCodec.mapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    ObjectMapper prettyMapper = DatabindCodec.prettyMapper();
    prettyMapper.registerModule(new JavaTimeModule());
    prettyMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    Future<RouterBuilder> routerFuture = RouterBuilder.create(vertx, "docs/openapi2.yaml");
    Future<JWTAuth> authFuture = JwtAuthProvider.init(vertx, config());
    // init SharedWorkerExecutor for this vertical
    BlockingExecutionUtil.initialize(vertx);

    List<ApdApiController> controllers = ControllerFactory.createControllers(vertx, config());

    Future.all(routerFuture, authFuture)
        .onSuccess(
            cf -> {
              RouterBuilder routerBuilder = cf.resultAt(0);
              JWTAuth jwtAuth = cf.resultAt(1);
              AuthenticationHandler authHandler = new KeycloakJwtAuthHandler(jwtAuth);
              AuthenticationHandler optionalAuth = new OptionalJwtAuthHandler(jwtAuth);
              try {

                LOGGER.debug("Adding platform handlers...");
                int timeout = config().getInteger("timeout", 100000); // Configurable timeout
                routerBuilder.rootHandler(TimeoutHandler.create(timeout, 408));
                routerBuilder.rootHandler(BodyHandler.create().setHandleFileUploads(false));

                LOGGER.debug("Registering controllers...");
                RouterBuilderOptions factoryOptions =
                    new RouterBuilderOptions().setMountResponseContentTypeHandler(true);
                routerBuilder.setOptions(factoryOptions);
                routerBuilder.securityHandler("authorization", authHandler);
                routerBuilder.securityHandler("optionalAuth", optionalAuth);

                controllers.forEach(controller -> controller.register(routerBuilder));

                LOGGER.debug("Creating router...");
                router = routerBuilder.createRouter();

                LOGGER.debug("Configuring CORS and error handlers...");
                configureCorsHandler(router);
                putCommonResponseHeaders();
                configureFailureHandler(router);
                configureErrorHandlers(router);

                LOGGER.debug("Starting HTTP server...");
                HttpServerOptions serverOptions = new HttpServerOptions();

                /* Documentation routes */
                router
                    .get(ROUTE_STATIC_SPEC)
                    .produces(APPLICATION_JSON)
                    .handler(
                        routingContext -> {
                          HttpServerResponse response = routingContext.response();
                          response.sendFile("docs/openapi2.yaml");
                        });
                router
                    .get(ROUTE_DOC)
                    .produces("text/html")
                    .handler(
                        routingContext -> {
                          HttpServerResponse response = routingContext.response();
                          response.sendFile("docs/apidoc.html");
                        });
                router
                    .get("/health/live")
                    .handler(
                        ctx -> {
                          ctx.response()
                              .setStatusCode(200)
                              .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
                              .end("Alive");
                        });

                setServerOptions(serverOptions);
                server = vertx.createHttpServer(serverOptions);
                server
                    .requestHandler(router)
                    .listen(
                        port,
                        http -> {
                          if (http.succeeded()) {
                            printDeployedEndpoints(router);
                            LOGGER.info("ApdApiServerVerticle  deployed on port: {}", port);
                          } else {
                            LOGGER.error(
                                "HTTP server failed to start: {}",
                                http.cause().getMessage(),
                                http.cause());
                          }
                        });
              } catch (Exception e) {
                LOGGER.error(
                    "Error during router creation or server startup: {}", e.getMessage(), e);
              }
            })
        .onFailure(
            failure -> {
              LOGGER.error(
                  "Failed to create RouterBuilder from OpenAPI spec: {}",
                  failure.getMessage(),
                  failure);
            });
  }

  private void configureCorsHandler(Router router) {
    CorsHandler corsHandler = CorsHandler.create();

    for (String origin : allowedOrigins) {
      corsHandler.addOrigin(origin);
    }

    corsHandler
        .allowedMethod(HttpMethod.GET)
        .allowedMethod(HttpMethod.POST)
        .allowedMethod(HttpMethod.OPTIONS)
        .allowedMethod(HttpMethod.PUT)
        .allowedMethod(HttpMethod.DELETE)
        .allowedMethod(HttpMethod.PATCH)
        .allowedHeader("Content-Type")
        .allowedHeader("Authorization")
        .allowedHeader("Origin")
        .allowCredentials(false);

    router.route().handler(corsHandler);
  }

  private void putCommonResponseHeaders() {
    router
        .route()
        .handler(
            ctx -> {
              ctx.response()
                  .putHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0")
                  .putHeader("Pragma", "no-cache")
                  .putHeader("Expires", "0")
                  .putHeader("X-Content-Type-Options", "nosniff");
              ctx.next();
            });
  }

  private void configureErrorHandlers(Router router) {

    router.errorHandler(
        401,
        ctx -> {
          HttpServerResponse response = ctx.response();
          if (response.headWritten()) {
            try {
              response.reset();
            } catch (RuntimeException e) {
              LOGGER.error(
                  "Failed to reset response: {}", HelperUtils.convertStackTrace(e).encode());
            }
            return;
          }
          response
              .setStatusCode(401)
              .putHeader(CONTENT_TYPE, APPLICATION_JSON)
              .end("not implemented");
        });
  }

  private void setServerOptions(HttpServerOptions serverOptions) {
    boolean isSsl = config().getBoolean("ssl", false);
    if (isSsl) {
      LOGGER.info("Info: Starting HTTPs server");
      String keystore = config().getString("keystore");
      String keystorePassword = config().getString("keystorePassword");
      serverOptions
          .setSsl(true)
          .setKeyCertOptions(new KeyStoreOptions().setPath(keystore).setPassword(keystorePassword));
    } else {
      LOGGER.info("Info: Starting HTTP server");
      serverOptions.setSsl(false);
    }
  }

  private void configureFailureHandler(Router router) {
    router.route().failureHandler(new FailureHandler());
  }

  private void printDeployedEndpoints(Router router) {
    for (Route route : router.getRoutes()) {
      if (route.getPath() != null) {
        LOGGER.info("Deployed endpoint [{}] {}", route.methods(), route.getPath());
      }
    }
  }

  @Override
  public void stop() {
    if (server != null) {
      server.close();
    }
  }
}
