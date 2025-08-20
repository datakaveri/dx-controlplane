package org.cdpg.dx.aaa.apiserver;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.*;
import static org.cdpg.dx.common.config.CorsUtil.allowedOrigins;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.openapi.RouterBuilderOptions;
import io.vertx.serviceproxy.HelperUtils;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.handler.AAAJwtAuthHandler;
import org.cdpg.dx.auth.authentication.handler.KeycloakJwtAuthHandler;
import org.cdpg.dx.auth.authentication.handler.OptionalJwtAuthHandler;
import org.cdpg.dx.auth.authentication.provider.JwtAuthProvider;
import org.cdpg.dx.auth.authentication.util.ChainedJwtAuthHandler;
import org.cdpg.dx.auth.authentication.util.TokenIssuer;
import org.cdpg.dx.common.FailureHandler;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.util.BlockingExecutionUtil;

public class ApiServerVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LogManager.getLogger(ApiServerVerticle.class);
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
    port = config().getInteger("httpPort", 8443);
    allowedOrigins = config().getJsonArray("corsAllowedOrigin").getList();

    ObjectMapper mapper = DatabindCodec.mapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    DatabindCodec.mapper().setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);

    ObjectMapper prettyMapper = mapper.copy();
    prettyMapper.enable(SerializationFeature.INDENT_OUTPUT);
    DatabindCodec.prettyMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);

    Future<RouterBuilder> routerFuture = RouterBuilder.create(vertx, "docs/openapi.yaml");
      Future<JWTAuth> keyCloakFuture = JwtAuthProvider.init(vertx, config(), TokenIssuer.KEYCLOAK);
      Future<JWTAuth> aaaAuthFuture = JwtAuthProvider.init(vertx, config(), TokenIssuer.AAA);    // init SharedWorkerExecutor for this vertical
    BlockingExecutionUtil.initialize(vertx);

    List<ApiController> controllers = ControllerFactory.createControllers(vertx, config());

      Future.all(routerFuture, aaaAuthFuture,keyCloakFuture)
        .onSuccess(
            cf -> {
              RouterBuilder routerBuilder = cf.resultAt(0);
                JWTAuth aaaJwtAuth = cf.resultAt(1);
                JWTAuth keyCloakJwtAuth = cf.resultAt(2);

              AuthenticationHandler keycloakJwtAuthHandler = new KeycloakJwtAuthHandler(keyCloakJwtAuth);
              AuthenticationHandler optionalAuth = new OptionalJwtAuthHandler(keyCloakJwtAuth);
              AuthenticationHandler aaaAuthHandler = new AAAJwtAuthHandler(aaaJwtAuth);

                AuthenticationHandler chainedAuth = new ChainedJwtAuthHandler(
                        List.of(aaaAuthHandler,keycloakJwtAuthHandler)
                );

//                AuthenticationHandler keycloakJwtAuthHandler = JWTAuthHandler.create(keyCloakJwtAuth);
//                AuthenticationHandler optionalAuth = new OptionalJwtAuthHandler(keyCloakJwtAuth);
//                AuthenticationHandler aaaAuthHandler = JWTAuthHandler.create(aaaJwtAuth);
//                AuthenticationHandler a=ChainAuthHandler.any().add(aaaAuthHandler).add(keycloakJwtAuthHandler);

                try {

                LOGGER.debug("Adding platform handlers...");
                int timeout = config().getInteger("timeout", 100000); // Configurable timeout
                routerBuilder.rootHandler(TimeoutHandler.create(timeout, 408));
                routerBuilder.rootHandler(BodyHandler.create().setHandleFileUploads(false));

                LOGGER.debug("Registering controllers...");
                RouterBuilderOptions factoryOptions =
                    new RouterBuilderOptions().setMountResponseContentTypeHandler(true);
                routerBuilder.setOptions(factoryOptions);
                routerBuilder.securityHandler("authorization", chainedAuth);
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
                          response.sendFile("docs/openapi.yaml");
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
                            LOGGER.info("ApiServerVerticle  deployed on port: {}", port);
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

    if (allowedOrigins.contains("*")) {
      corsHandler = CorsHandler.create("*").allowCredentials(false);
    } else {
      corsHandler = CorsHandler.create();
      for (String origin : allowedOrigins) {
        corsHandler.addOrigin(origin);
      }
      corsHandler.allowCredentials(true);
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
        .allowedHeader("Origin");

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
