package org.cdpg.dx.common.metrics;

import com.nimbusds.jose.jwk.JWK;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.vertx.core.Vertx;
import io.vertx.core.net.JksOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.Collections.list;

/**
 * Publishes the expiry of every private-key entry in a keystore as a Prometheus gauge, so
 * certificate expiry can be alerted on ahead of time instead of being discovered on the day.
 *
 * <p>For the JWT signing keystore, note that the EC key pair itself does not expire, and neither
 * Nimbus nor Vert.x validates certificate dates when loading the key — token signing keeps working
 * past {@code notAfter}. What breaks is any relying party validating the {@code x5c} chain
 * published in the JWKS. Since the keystore is read once at startup, rotation also needs a restart,
 * so this metric exists to give enough lead time to schedule one.
 *
 * <p>The gauge is the absolute expiry as a Unix timestamp rather than a countdown: the value is
 * static, so a stale scrape can never make a countdown look healthier than it is, and the threshold
 * lives in the alert rule where it can be tuned without a redeploy.
 *
 * <pre>
 * jwt_signing_cert_expiry_timestamp_seconds{alias="jwt-key-1",kid="..."} 1793836800
 *
 * - alert: JwtSigningCertExpiringSoon
 *   expr: (jwt_signing_cert_expiry_timestamp_seconds - time()) / 86400 &lt; 30
 *   for: 1h
 * </pre>
 *
 * <p>Every key entry is reported rather than one configured alias, so a rotation that temporarily
 * holds both the old and new key produces a series for each without any code change.
 */
public final class KeystoreExpiryMetrics implements MeterBinder {

  private static final Logger LOGGER = LogManager.getLogger(KeystoreExpiryMetrics.class);
  private static final String METRIC_NAME = "jwt_signing_cert_expiry_timestamp_seconds";

  private final KeyStore keyStore;

  public KeystoreExpiryMetrics(KeyStore keyStore) {
    this.keyStore = keyStore;
  }

  /** Loads a JKS keystore so the binder can be built straight from config values. */
  public static KeystoreExpiryMetrics fromJks(Vertx vertx, String path, String password) {
    JksOptions options = new JksOptions().setPath(path).setPassword(password);
    try {
      return new KeystoreExpiryMetrics(options.loadKeyStore(vertx));
    } catch (Exception e) {
      throw new IllegalStateException("Could not load keystore for expiry metrics: " + path, e);
    }
  }

  /**
   * Binds to the default Vert.x Micrometer registry, doing nothing if metrics are disabled.
   * Instrumentation must never be the reason the server fails to start, so failures are logged and
   * swallowed.
   */
  public static void bindToDefaultRegistry(Vertx vertx, String path, String password) {
    try {
      MeterRegistry registry = BackendRegistries.getDefaultNow();
      if (registry == null) {
        LOGGER.debug("No metrics registry available; skipping '{}' gauge.", METRIC_NAME);
        return;
      }
      fromJks(vertx, path, password).bindTo(registry);
    } catch (Exception e) {
      LOGGER.warn("Could not publish keystore expiry metrics: {}", e.getMessage(), e);
    }
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    try {
      for (String alias : list(keyStore.aliases())) {
        if (!keyStore.isKeyEntry(alias)) {
          continue;
        }
        if (!(keyStore.getCertificate(alias) instanceof X509Certificate x509)) {
          LOGGER.warn("No X.509 certificate for key alias '{}'; skipping expiry metric.", alias);
          continue;
        }
        bindCertificate(registry, alias, x509);
      }
    } catch (Exception e) {
      LOGGER.warn("Could not publish keystore expiry metrics: {}", e.getMessage(), e);
    }
  }

  private void bindCertificate(MeterRegistry registry, String alias, X509Certificate x509) {
    Instant expiry = x509.getNotAfter().toInstant();
    long daysRemaining = Duration.between(Instant.now(), expiry).toDays();
    String kid = thumbprintOf(x509);

    if (daysRemaining < 0) {
      LOGGER.warn(
          "Signing certificate for alias '{}' EXPIRED on {} ({} days ago). Token signing is "
              + "unaffected, but relying parties validating the x5c chain will reject. kid={}",
          alias,
          expiry,
          -daysRemaining,
          kid);
    } else {
      LOGGER.info(
          "Signing certificate for alias '{}' expires {} ({} days remaining). kid={}",
          alias,
          expiry,
          daysRemaining,
          kid);
    }

    // Micrometer de-duplicates by meter id, so binding twice in one JVM is a no-op rather than a
    // duplicate series.
    Gauge.builder(METRIC_NAME, expiry, e -> (double) e.getEpochSecond())
        .description(
            "Unix timestamp at which the X.509 certificate wrapping the signing key expires")
        .tag("alias", alias)
        .tag("kid", kid)
        .strongReference(true)
        .register(registry);
  }

  /**
   * RFC 7638 thumbprint of the certificate's public key. This matches the {@code kid} published in
   * the JWKS — the thumbprint covers only public key members, so it can be derived from the
   * certificate without the key password.
   */
  private static String thumbprintOf(X509Certificate x509) {
    try {
      return JWK.parse(x509).computeThumbprint().toString();
    } catch (Exception e) {
      LOGGER.debug("Could not compute thumbprint for certificate: {}", e.getMessage());
      return "unknown";
    }
  }
}
