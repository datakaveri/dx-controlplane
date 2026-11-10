package org.cdpg.dx.scheduler.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Vertx;
@VertxGen
@ProxyGen
public interface SchedulerService {
    @GenIgnore
    static SchedulerService createProxy(Vertx vertx, String address) {
        return new SchedulerServiceVertxEBProxy(vertx, address);
    }
}
