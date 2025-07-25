// CatalogueClient.java

package org.cdpg.dx.catalogueService.client;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import org.cdpg.dx.common.exception.DxNotFoundException;

public class CatalogueClient {

  private static final Logger LOGGER = LogManager.getLogger(CatalogueClient.class);

  private final WebClient client;
  private final int port;
  private final String host;

  public CatalogueClient(WebClient client, int port, String host) {
    this.client = client;
    this.port = port;
    this.host = host;
  }

  public Future<JsonArray> get(String path, Map<String, String> queryParams) {
    HttpRequest<io.vertx.core.buffer.Buffer> request = client.get(port, host, path);
    queryParams.forEach(request::addQueryParam);

    return request.send()
        .compose(response -> {
          if (response.statusCode() != 200) {
            LOGGER.error("Non-200 response from catalogue: {}", response.statusCode());
            if (response.statusCode() == 404) {
              return Future.failedFuture(new DxNotFoundException("Catalogue item not found"));
            }
            return Future.failedFuture(new DxInternalServerErrorException("Catalogue service returned non-200 status"));
          }

          JsonObject body = response.bodyAsJsonObject();
          String type = body.getString("type");
          JsonArray result = body.getJsonArray("results");
          if ("urn:dx:cat:Success".equals(type) && result != null) {
            return Future.succeededFuture(result);
          } else {
            LOGGER.error("Unexpected catalogue response type or structure: {}", body.encode());
            return Future.failedFuture(new DxInternalServerErrorException("Invalid catalogue response format"));
          }
        })
        .recover(err -> {
          LOGGER.error("Catalogue request failed: {}", err.getMessage(), err);
          if (err instanceof DxNotFoundException) {
            return Future.failedFuture(err); // propagate 404
          }
          return Future.failedFuture(new DxInternalServerErrorException(err.getMessage()));
        });
  }
}
