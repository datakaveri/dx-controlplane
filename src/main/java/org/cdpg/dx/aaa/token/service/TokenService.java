package org.cdpg.dx.aaa.token.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import jakarta.json.Json;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.Date;

public interface TokenService {

  // Method to create a JWT token
   Future<JsonObject> createToken(String clientId, String clientSecret);

     JsonObject generateJwks();
}
