package org.cdpg.dx.aaa.summary.dao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface UsageAggregationDao {
  Future<JsonObject> aggregateUsage();
}
