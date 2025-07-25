package org.cdpg.dx.aaa.kyc.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

public interface KYCService {

    Future<JsonObject> getKYCData(UUID userId, String authCode, String codeVerifier);

    Future<JsonObject> confirmKYCData(UUID userId, String codeVerifier, String userName);

}
