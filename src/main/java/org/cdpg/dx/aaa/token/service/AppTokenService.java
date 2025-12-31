package org.cdpg.dx.aaa.token.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.token.model.AppTokenRequest;

public interface AppTokenService {
  Future<JsonObject> createToken(AppTokenRequest request);
}
