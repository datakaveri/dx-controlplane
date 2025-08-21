package org.cdpg.dx.auth.authentication.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.publicKey.service.PublicService;
import org.cdpg.dx.auth.authentication.util.TokenIssuer;

/** Fetches JWKS either from HTTP (Keycloak) or from an internal provider (AAA). */
public class JwksClient {
  private static final Logger LOGGER = LogManager.getLogger(JwksClient.class);
  private final String certUrl;
  private final WebClient client;

  private final Vertx vertx;
  private final TokenIssuer issuer;
  private final PublicService aaaKeyProvider;

  public JwksClient(Vertx vertx, String certUrl, TokenIssuer issuer, PublicService aaaKeyProvider) {
    this.vertx = vertx;
    this.certUrl = certUrl;
    this.issuer = issuer;
    this.aaaKeyProvider = aaaKeyProvider;
    this.client =
        WebClient.create(
            vertx, new WebClientOptions().setSsl(certUrl.startsWith("https")).setTrustAll(true));
  }

  public Future<JsonObject> fetchJwkKeys() {
    if (issuer == TokenIssuer.AAA) {
      // Call internal AAA provider method instead of HTTP
      return Future.succeededFuture(aaaKeyProvider.generateJwks());
    } else {
      return client
          .requestAbs(HttpMethod.GET, certUrl)
          .send()
          .compose(
              resp -> {
                if (resp.statusCode() == 200 && resp.bodyAsJsonObject().containsKey("keys")) {
                  return Future.succeededFuture(resp.bodyAsJsonObject());
                } else {
                  return Future.failedFuture("Invalid JWKs response: " + resp.statusCode());
                }
              })
          .recover(
              err -> {
                LOGGER.error("Failed to fetch JWKs: {}", err.getMessage());
                return Future.failedFuture(err);
              });
    }
  }
}
