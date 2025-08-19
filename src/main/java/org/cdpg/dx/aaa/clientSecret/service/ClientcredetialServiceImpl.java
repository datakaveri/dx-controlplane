package org.cdpg.dx.aaa.clientSecret.service;

import io.vertx.core.Future;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.clientSecret.dao.ClientcredetialDao;
import org.cdpg.dx.aaa.clientSecret.model.ClientCredentials;

public class ClientcredetialServiceImpl implements ClientcredetialService {
  public static final int CLIENT_SECRET_BYTES = 20;
  private final Logger LOGGER = LogManager.getLogger(ClientcredetialServiceImpl.class);
  private final ClientcredetialDao clientcredetialDao;
  private final Supplier<UUID> clientIdSupplier = UUID::randomUUID;
  private final Supplier<String> randomSecretSupplier =
      () -> {
        byte[] randBytes = new byte[CLIENT_SECRET_BYTES];
        new SecureRandom().nextBytes(randBytes);
        return Hex.encodeHexString(randBytes);
      };

  public ClientcredetialServiceImpl(ClientcredetialDao clientcredetialDao) {
    this.clientcredetialDao = clientcredetialDao;
  }

  @Override
  public Future<ClientCredentials> createClientIdAndClientSecret(UUID userId) {

    UUID clientId = clientIdSupplier.get();
    String hashedClientId = DigestUtils.sha512Hex(clientId.toString());
    String clientSecret = randomSecretSupplier.get();
    String hashedClientSecret = DigestUtils.sha512Hex(clientSecret);

    ClientCredentials clientCredentials =
        new ClientCredentials(userId, hashedClientId, hashedClientSecret, null);
    LOGGER.debug(
        "Creating client credentials for userId: {}, clientId: {}, clientSecret: {}",
        userId,
        clientId,
        clientSecret);

    LOGGER.debug(
        "userId : {}, hashedClientId: {}, hashedClientSecret: {}",
        userId,
        hashedClientId,
        hashedClientSecret);

    return clientcredetialDao
        .saveClientCredentials(clientCredentials)
        .onSuccess(
            savedCredentials ->
                LOGGER.info("Client credentials saved successfully for userId: {}", userId))
        .onFailure(throwable -> LOGGER.error("Failed to save client credentials", throwable))
        .map(v -> new ClientCredentials(userId, clientId.toString(), clientSecret, v.createdAt()));
  }

  @Override
  public Future<UUID> getUserIdByClientIdAndSecret(String clientId, String clientSecret) {
    return clientcredetialDao
        .getClientCredentialsByClientId(clientId, clientSecret)
        .map(UUID::fromString);
  }
}
