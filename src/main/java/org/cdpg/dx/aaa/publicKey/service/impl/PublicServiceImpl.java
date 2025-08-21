package org.cdpg.dx.aaa.publicKey.service.impl;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import java.security.KeyStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.publicKey.service.PublicService;

public class PublicServiceImpl implements PublicService {
  public static final String JWT_ALGORITHM = "ES256";
  private static final Logger LOGGER = LogManager.getLogger(PublicServiceImpl.class);
  private final String keystorePath;
  private final String keystorePassword;
  private final Vertx vertx;

  public PublicServiceImpl(String keystorePath, String keystorePassword, Vertx vertx) {
    this.keystorePath = keystorePath;
    this.keystorePassword = keystorePassword;
    this.vertx = vertx;
  }

  @Override
  public JsonObject generateJwks() {
      LOGGER.debug("Generating JWKS from JKS keystore at: {}", keystorePath);
    try {
      JksOptions options = new JksOptions().setPath(keystorePath).setPassword(keystorePassword);
      KeyStore ks = options.loadKeyStore(vertx);
      ECKey ecKey = ECKey.load(ks, JWT_ALGORITHM, keystorePassword.toCharArray());

      JWKSet jwkSet = new JWKSet(ecKey.toPublicJWK());

      return new JsonObject(jwkSet.toJSONObject(true));

    } catch (Exception e) {
      LOGGER.error("Error retrieving public key from JKS: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to retrieve public key from JKS", e);
    }
  }
}
