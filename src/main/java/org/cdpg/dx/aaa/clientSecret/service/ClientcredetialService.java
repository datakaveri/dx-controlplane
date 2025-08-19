package org.cdpg.dx.aaa.clientSecret.service;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.clientSecret.model.ClientCredentials;

public interface ClientcredetialService {

  Future<ClientCredentials> createClientIdAndClientSecret(UUID userId);

  Future<UUID> getUserIdByClientIdAndSecret(String clientId, String clientSecret);
}
