package org.cdpg.dx.auth.authentication.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JwksClient {
    private static final Logger LOGGER = LogManager.getLogger(JwksClient.class);

    private final String certUrl;
    private final WebClient client;

    public JwksClient(Vertx vertx, String certUrl) {
        this.certUrl = certUrl;
        this.client = WebClient.create(vertx, new WebClientOptions().setSsl(certUrl.startsWith("https")).setTrustAll(true));
    }

    public Future<JsonObject> fetchJwkKeys() {
        LOGGER.info("Fetching JWKs from {}", certUrl);
        return client
                .requestAbs(HttpMethod.GET, certUrl)
                .send()
                .compose(resp -> {

                    if (resp.statusCode() == 200 && resp.bodyAsJsonObject().containsKey("keys")) {
                        return Future.succeededFuture(resp.bodyAsJsonObject());
                    } else {
                        return Future.failedFuture("Invalid JWKs response: " + resp.statusCode());
                    }
                })
                .recover(err -> {
                    LOGGER.error("Failed to fetch JWKs: {}", err.getMessage());
                    err.printStackTrace();
                    return Future.failedFuture(err);
                });
    }
}
