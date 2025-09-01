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

public class JwksClient {
  private static final Logger LOGGER = LogManager.getLogger(JwksClient.class);
  private final WebClient client;
  private final PublicService aaaKeyProvider;

  public JwksClient(Vertx vertx, PublicService aaaKeyProvider) {
    this.client = WebClient.create(vertx, new WebClientOptions().setTrustAll(true));
    this.aaaKeyProvider = aaaKeyProvider;
  }

  /**
   * Fetch JWKS for an issuer.
   *
   * @param type "internal" or "remote"
   * @param url JWKS endpoint (required if type=remote)
   */
  public Future<JsonObject> fetchJwks(String type, String url) {
    if ("internal".equalsIgnoreCase(type)) {
      return Future.succeededFuture(aaaKeyProvider.generateJwks());
    }

    if ("remote".equals(type)) {
      return client
          .requestAbs(HttpMethod.GET, url)
          .send()
          .compose(
              resp -> {
                if (resp.statusCode() == 200 && resp.bodyAsJsonObject().containsKey("keys")) {
                  return Future.succeededFuture(resp.bodyAsJsonObject());
                } else {
                  return Future.failedFuture("Invalid JWKS response: " + resp.statusCode());
                }
              })
          .recover(
              err -> {
                LOGGER.error("Failed to fetch JWKs from {}: {}", url, err.getMessage());
                return Future.failedFuture(err);
              });
    }

    return Future.failedFuture("Unknown issuer type: " + type);
  }
}
