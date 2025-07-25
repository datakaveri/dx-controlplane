package org.cdpg.dx.aaa.user.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.model.DxUser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;

public interface UserService {

    Future<DxUser> getUserInfo(DxUser dxUser);
    Future<DxUser> getUserInfoByID(UUID userId);

    default <T> Future<List<JsonObject>> enrichWithUserRoles(
            List<T> items,
            Function<T, UUID> userIdExtractor,
            Function<T, JsonObject> baseJsonMapper
    ) {
        List<Future<JsonObject>> futures = items.stream()
                .map(item ->
                        getUserInfoByID(userIdExtractor.apply(item))
                                .map(user -> {
                                    JsonObject enriched = baseJsonMapper.apply(item);
                                    enriched.put("roles", user.roles());
                                    enriched.put("account_enabled", user.account_enabled());
                                    return enriched;
                                })
                )
                .toList();

        return Future.all(futures)
                .map(cf ->
                        IntStream.range(0, cf.size())
                                .mapToObj(cf::resultAt)
                                .map(result -> (JsonObject) result)
                                .toList()
                );
    }

}
