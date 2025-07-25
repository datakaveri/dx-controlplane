package org.cdpg.dx.keycloak.factory;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class KeycloakServiceFactory {
    public static KeycloakUserService create(JsonObject config) {
        return new KeycloakUserServiceImpl(config);
    }
}
