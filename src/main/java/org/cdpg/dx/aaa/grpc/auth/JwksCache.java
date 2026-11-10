package org.cdpg.dx.aaa.grpc.auth;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Caches Keycloak's JWKS (public keys) used by {@link ServiceAuthInterceptor} to validate service
 * identity tokens. Implements {@link JWKSource} so it plugs directly into Nimbus
 * {@code JWSVerificationKeySelector}.
 *
 * <p>Thread-safe: refresh is synchronized with double-checked locking. Force-refresh is available
 * for handling Keycloak key rotation (unknown kid scenario).
 */
public class JwksCache implements JWKSource<SecurityContext> {

  private static final Logger LOGGER = LogManager.getLogger(JwksCache.class);

  private final String jwksUrl;
  private final long cacheTtlSeconds;

  private volatile JWKSet cachedJwkSet;
  private volatile Instant lastRefreshed = Instant.EPOCH;

  public JwksCache(String jwksUrl, long cacheTtlSeconds) {
    this.jwksUrl = jwksUrl;
    this.cacheTtlSeconds = cacheTtlSeconds;
  }

  @Override
  public List<JWK> get(JWKSelector selector, SecurityContext context) throws KeySourceException {
    try {
      return selector.select(getJwkSet());
    } catch (KeySourceException e) {
      throw e;
    } catch (Exception e) {
      throw new KeySourceException("Failed to get JWKS from " + jwksUrl, e);
    }
  }

  public JWKSet getJwkSet() throws Exception {
    if (needsRefresh()) {
      refresh();
    }
    return cachedJwkSet;
  }

  /** Force-refreshes the cache; used when a token contains an unknown kid (key rotation). */
  public void forceRefresh() {
    try {
      synchronized (this) {
        lastRefreshed = Instant.EPOCH;
      }
      refresh();
    } catch (Exception e) {
      LOGGER.error("JWKS force-refresh failed: {}", e.getMessage());
    }
  }

  private boolean needsRefresh() {
    return cachedJwkSet == null
        || Instant.now().isAfter(lastRefreshed.plusSeconds(cacheTtlSeconds));
  }

  private synchronized void refresh() throws Exception {
    if (!needsRefresh()) return;
    LOGGER.info("Refreshing JWKS from {}", jwksUrl);
    cachedJwkSet = JWKSet.load(new URL(jwksUrl));
    lastRefreshed = Instant.now();
    LOGGER.info("JWKS refreshed: {} keys loaded", cachedJwkSet.getKeys().size());
  }
}
