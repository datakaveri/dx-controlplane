package org.cdpg.dx.keycloak.client;

import io.vertx.core.json.JsonObject;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

public class KeycloakClientProvider {
    private static Keycloak keycloakInstance;

    public static Keycloak getInstance(JsonObject config) {
        if (keycloakInstance == null) {
            keycloakInstance = KeycloakBuilder.builder()
                    .serverUrl(config.getString("keycloakUrl"))
                    .realm(config.getString("keycloakRealm")) // Auth realm to log in as admin
                    .clientId(config.getString("keycloakAdminClientId"))
                    .clientSecret(config.getString("keycloakAdminClientSecret"))
                    .grantType("client_credentials")
                    .build();
        }
        return keycloakInstance;
    }
}