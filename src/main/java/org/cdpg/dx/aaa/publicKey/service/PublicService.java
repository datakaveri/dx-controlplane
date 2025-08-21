package org.cdpg.dx.aaa.publicKey.service;

import io.vertx.core.json.JsonObject;

public interface PublicService {

  JsonObject generateJwks();
}
