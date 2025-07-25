package org.cdpg.dx.common.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.common.exception.DxValidationException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public class RequestHelper {

    public static UUID getPathParamAsUUID(RoutingContext ctx, String name) {
        String value = ctx.pathParam(name);
        if (value == null || value.isBlank()) {
            throw new DxValidationException("Missing required path param: " + name);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new DxValidationException("Invalid UUID format for path param: " + name);
        }
    }

    public static Optional<String> getPathParam(RoutingContext ctx, String name) {
        return Optional.ofNullable(ctx.pathParam(name));
    }

    public static JsonObject getBodyJson(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        return body != null ? body : new JsonObject();
    }

    public static <T> T parseAndValidateBody(RoutingContext ctx, Set<String> requiredKeys, Function<JsonObject, T> mapper) {
        JsonObject json = getBodyJson(ctx);

        for (String key : requiredKeys) {
            if (!json.containsKey(key) || json.getValue(key) == null) {
                throw new IllegalArgumentException("Missing required body field: " + key);
            }
        }

        return mapper.apply(json);
    }

    public static <T> T parseBody(RoutingContext ctx, Function<JsonObject, T> mapper) {
        return mapper.apply(getBodyJson(ctx));
    }

    /**
     * Merges additional key-values from a Map into the request body and maps the result.
     *
     * @param ctx    the routing context
     * @param extra  additional key-value pairs to merge into the body
     * @param mapper function to map merged JsonObject to a Java object
     * @param <T>    return type
     * @return mapped object of type T
     */
    public static <T> T mergeBodyAndParse(RoutingContext ctx, Map<String, Object> extra, Function<JsonObject, T> mapper) {
        JsonObject merged = getBodyJson(ctx).copy();
        if (extra != null) {
            extra.forEach(merged::put);
        }
        return mapper.apply(merged);
    }
}
