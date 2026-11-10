package org.cdpg.dx.aaa.grpc.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * gRPC {@link ServerInterceptor} that validates the service identity token on every inbound call.
 *
 * <p>Checks (in order):
 * <ol>
 *   <li>Bearer token present in {@code authorization} metadata
 *   <li>RS256 signature valid against Keycloak JWKS
 *   <li>{@code exp} in the future
 *   <li>{@code aud} contains {@code dx-controlplane}
 *   <li>{@code scope} contains {@code grpc:controlplane}
 *   <li>{@code azp} is in the configured {@code grpcAllowedServiceClients} whitelist
 * </ol>
 *
 * <p>Any check failure returns {@code UNAUTHENTICATED} and the RPC handler is never invoked.
 * On success, the caller's service identity ({@code azp}) is stored in {@link #CALLER_SERVICE_KEY}
 * for use by RPC handlers if needed.
 */
public class ServiceAuthInterceptor implements ServerInterceptor {

  private static final Logger LOGGER = LogManager.getLogger(ServiceAuthInterceptor.class);

  private static final Metadata.Key<String> AUTHORIZATION_KEY =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

  /** gRPC Context key holding the calling service's client ID (azp claim). */
  public static final Context.Key<String> CALLER_SERVICE_KEY = Context.key("callerService");

  private final JwksCache jwksCache;
  private final Set<String> allowedServiceClients;

  public ServiceAuthInterceptor(JwksCache jwksCache, Set<String> allowedServiceClients) {
    this.jwksCache = jwksCache;
    this.allowedServiceClients = allowedServiceClients;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    String authHeader = headers.get(AUTHORIZATION_KEY);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      call.close(
          Status.UNAUTHENTICATED.withDescription("Missing Bearer token"), new Metadata());
      return new ServerCall.Listener<>() {};
    }

    String rawToken = authHeader.substring(7);

    try {
      JWTClaimsSet claims = verifyAndGetClaims(rawToken);

      if (claims.getExpirationTime() == null
          || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
        call.close(Status.UNAUTHENTICATED.withDescription("Token expired"), new Metadata());
        return new ServerCall.Listener<>() {};
      }

      List<String> audience = claims.getAudience();
      if (audience == null || !audience.contains("dx-controlplane")) {
        call.close(Status.UNAUTHENTICATED.withDescription("Invalid audience"), new Metadata());
        return new ServerCall.Listener<>() {};
      }

      String scope = (String) claims.getClaim("scope");
      if (scope == null || !scope.contains("grpc:controlplane")) {
        call.close(
            Status.UNAUTHENTICATED.withDescription("Missing required scope grpc:controlplane"),
            new Metadata());
        return new ServerCall.Listener<>() {};
      }

      String azp = (String) claims.getClaim("azp");
      if (azp == null || !allowedServiceClients.contains(azp)) {
        LOGGER.warn(
            "gRPC call rejected: azp={} not in allowedClients={}", azp, allowedServiceClients);
        call.close(
            Status.UNAUTHENTICATED.withDescription("Service client not authorized"),
            new Metadata());
        return new ServerCall.Listener<>() {};
      }

      LOGGER.debug("gRPC call authenticated: callerService={}", azp);
      Context ctx = Context.current().withValue(CALLER_SERVICE_KEY, azp);
      return Contexts.interceptCall(ctx, call, headers, next);

    } catch (Exception e) {
      LOGGER.error("gRPC token validation failed: {}", e.getMessage());
      call.close(
          Status.UNAUTHENTICATED.withDescription("Token validation failed"), new Metadata());
      return new ServerCall.Listener<>() {};
    }
  }

  private JWTClaimsSet verifyAndGetClaims(String rawToken) throws Exception {
    try {
      return buildProcessor().process(rawToken, null);
    } catch (BadJOSEException e) {
      // Unknown kid — Keycloak may have rotated keys; refresh cache and retry once
      LOGGER.warn("BadJOSEException (possible key rotation), force-refreshing JWKS and retrying");
      jwksCache.forceRefresh();
      return buildProcessor().process(rawToken, null);
    }
  }

  private DefaultJWTProcessor<SecurityContext> buildProcessor() {
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwksCache));
    return processor;
  }
}
