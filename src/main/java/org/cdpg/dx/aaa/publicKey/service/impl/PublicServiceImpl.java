package org.cdpg.dx.aaa.publicKey.service.impl;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import java.security.KeyStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.publicKey.service.PublicService;

public class PublicServiceImpl implements PublicService {

  private static final Logger LOGGER = LogManager.getLogger(PublicServiceImpl.class);
  private static final String KEY_ALIAS = "jwt-key-1";

  private final JsonObject cachedJwks;
  private final String kid;

  public PublicServiceImpl(String keystorePath, String keystorePassword, Vertx vertx) {
    this.cachedJwks = buildJwks(keystorePath, keystorePassword, vertx);
    this.kid = cachedJwks.getJsonArray("keys").getJsonObject(0).getString("kid");
  }

  private JsonObject buildJwks(String keystorePath, String keystorePassword, Vertx vertx) {
    LOGGER.info("Loading JWKS from keystore at startup: {}", keystorePath);
    try {
      JksOptions options = new JksOptions().setPath(keystorePath).setPassword(keystorePassword);
      KeyStore ks = options.loadKeyStore(vertx);
      ECKey loadedKey = ECKey.load(ks, KEY_ALIAS, keystorePassword.toCharArray());

      if (!Curve.P_256.equals(loadedKey.getCurve())) {
        throw new IllegalArgumentException(
            "Keystore key must use P-256 curve for ES256, found: " + loadedKey.getCurve());
      }

      ECKey publicKey =
          new ECKey.Builder(Curve.P_256, loadedKey.toECPublicKey())
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.ES256)
              .keyID(loadedKey.computeThumbprint().toString())
              .x509CertChain(loadedKey.getX509CertChain())
              .x509CertSHA256Thumbprint(loadedKey.getX509CertSHA256Thumbprint())
              .build();

      JsonObject jwks = new JsonObject(new JWKSet(publicKey).toJSONObject(true));
      LOGGER.info("JWKS loaded and cached successfully. kid={}", publicKey.getKeyID());
      return jwks;

    } catch (Exception e) {
      LOGGER.error("Failed to load public key from keystore: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to load public key from keystore at startup", e);
    }
  }

  @Override
  public JsonObject generateJwks() {
    return cachedJwks;
  }

  @Override
  public String getKid() {
    return kid;
  }

}
