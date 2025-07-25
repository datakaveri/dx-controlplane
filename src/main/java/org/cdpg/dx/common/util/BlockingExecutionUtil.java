package org.cdpg.dx.common.util;


import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

import java.util.function.Supplier;

public class BlockingExecutionUtil {
    private static final String POOL_NAME = "keycloak-worker-pool";
    private static final int MAX_WORKERS = 10;

    private static WorkerExecutor executor;

    public static void initialize(Vertx vertx) {
        if (executor == null) {
            executor = vertx.createSharedWorkerExecutor(POOL_NAME, MAX_WORKERS);
        }
    }

    public static <T> Future<T> runBlocking(Supplier<T> supplier) {
        return Future.future(promise ->
                executor.executeBlocking(promise0 -> {
                    try {
                        promise0.complete(supplier.get());
                    } catch (Exception e) {
                        promise0.fail(e);
                    }
                }, false, promise)
        );
    }
}
