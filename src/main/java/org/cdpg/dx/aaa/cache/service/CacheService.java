package org.cdpg.dx.aaa.cache.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface CacheService {

void store(String key, JsonObject data);

Future<JsonObject> retrieve(String key);
}
