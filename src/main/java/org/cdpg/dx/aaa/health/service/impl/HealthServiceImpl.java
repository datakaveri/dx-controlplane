package org.cdpg.dx.aaa.health.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.health.service.HealthService;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class HealthServiceImpl implements HealthService {
  private static final Logger LOGGER = LogManager.getLogger(HealthServiceImpl.class);
  private static final Duration MIN_INTERVAL = Duration.ofSeconds(10);
  private final Vertx vertx;
  private final PostgresService postgresService;
  // private final DataBrokerService dataBrokerService;
  private final long eventLoopDelayThresholdMs =
      500; // Threshold for event loop delay in milliseconds
  private Instant lastPing = Instant.MIN;

  public HealthServiceImpl(Vertx vertx, PostgresService postgresService) {
    this.vertx = vertx;
    this.postgresService = postgresService;
    // this.dataBrokerService = dataBrokerService;
  }

  @Override
  public Future<Void> checkLiveness() {
    Promise<Void> promise = Promise.promise();
    long startTime = System.nanoTime();

    vertx.runOnContext(
        v -> {
          long duration = System.nanoTime() - startTime;
          long delayMs = duration / 1_000_000;

          if (delayMs > eventLoopDelayThresholdMs) {
            String msg = "Event loop delay too high: " + delayMs + " ms";
            LOGGER.warn(msg);
            promise.fail(msg);
          } else {
            promise.complete();
          }
        });

    return promise.future();
  }

  public Future<Void> checkReadiness() {
    if (Duration.between(lastPing, Instant.now()).compareTo(MIN_INTERVAL) < 0) {
      return Future.succeededFuture();
    }

    lastPing = Instant.now();
    return postgresService
        .ping()
        .compose(ok -> ok ? Future.succeededFuture() : Future.failedFuture("DB not ready"));
  }
}
