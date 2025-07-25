package org.cdpg.dx.aaa.cache.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class CacheServiceImpl implements CacheService {

    private static final Logger LOG = LoggerFactory.getLogger(CacheServiceImpl.class);

    private final Cache<String, JsonObject> cache;

    public CacheServiceImpl() {
        // TTL-based cache setup: 10 minutes
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000) // optional size bound
                .build();
    }

    @Override
    public void store(String key, JsonObject data) {
        cache.put(key, data);
        LOG.debug("Stored cache key: {}", key);
    }

    @Override
    public Future<JsonObject> retrieve(String key) {
        JsonObject value = cache.getIfPresent(key);
        LOG.debug("Retrieved cache key: {}, found: {}", key, value != null);
        return Future.succeededFuture(value);
    }
}
