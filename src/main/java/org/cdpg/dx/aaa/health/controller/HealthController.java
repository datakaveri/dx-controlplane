package org.cdpg.dx.aaa.health.controller;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.health.service.HealthService;


public class HealthController {

  private static final Logger LOGGER = LogManager.getLogger(HealthController.class);
  private static final String CONTENT_TYPE_TEXT = "text/plain";

  private final Router router;
  private final HealthService healthService;

  public HealthController(Router router, HealthService healthService) {
    this.router = router;
    this.healthService = healthService;
  }

  public Router init() {
    router.get("/health/live").handler(this::handleLiveness);
    //router.get("/health/ready").handler(this::handleReadiness);
    return router;
  }

  private void handleLiveness(RoutingContext ctx) {
    respond(ctx, 200, "Alive");
  }

  private void handleReadiness(RoutingContext ctx) {
    healthService
        .checkReadiness()
        .onSuccess(v -> respond(ctx, 200, "Ready"))
        .onFailure(
            err -> {
              LOGGER.warn("Readiness check failed: {}", err.getMessage());
              respond(ctx, 503, "NOT READY");
            });
  }

  private void respond(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
        .setStatusCode(statusCode)
        .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_TEXT)
        .end(message);
  }
}
