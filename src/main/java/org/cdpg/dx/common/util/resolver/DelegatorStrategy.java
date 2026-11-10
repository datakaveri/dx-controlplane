package org.cdpg.dx.common.util.resolver;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

import java.util.UUID;

public interface  DelegatorStrategy {
  Future<UUID> resolveUserId(RoutingContext ctx);
  Future<UUID> resolveOrgId(RoutingContext ctx, String orgIdParam);
}
