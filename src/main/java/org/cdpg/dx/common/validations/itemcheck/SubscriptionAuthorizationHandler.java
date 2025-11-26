package org.cdpg.dx.common.validations.itemcheck;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenNoAccessException;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import org.cdpg.dx.common.util.RoutingContextHelper;

public class SubscriptionAuthorizationHandler implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(SubscriptionAuthorizationHandler.class);
  private final WebClient webClient;
  private final String checkItemAndFilterUrl;

  public SubscriptionAuthorizationHandler(String controlPlaneDomain) {
    this.webClient = WebClient.create(Vertx.vertx(), new WebClientOptions().setTrustAll(true));
    this.checkItemAndFilterUrl = controlPlaneDomain + "/iudx/v2/cat/item/access";
  }

  private Future<Boolean> hasAccess(JsonObject principal, String accessType) {
    Promise<Boolean> promise = Promise.promise();

    JsonObject cons = principal.getJsonObject("cons");
    if (cons == null) {
      promise.fail("cons not found");
      return promise.future();
    }

    JsonArray accessArray = cons.getJsonArray("access");

    if (accessArray == null || accessArray.isEmpty()) {
      promise.complete(false);
      return promise.future();
    }

    boolean match =
        accessArray.stream().map(String.class::cast).anyMatch(accessType::equalsIgnoreCase);

    promise.complete(match);
    return promise.future();
  }

  @Override
  public void handle(RoutingContext context) {
    LOGGER.info("Starting SubscriptionAuthorizationHandler");

    if (context.user().principal().containsKey("cons")) {
      LOGGER.debug("Processing access token");

      JsonObject principal = context.user().principal();
      hasAccess(principal, "sub")
          .onSuccess(
              access -> {
                if (!access) {
                  context.fail(new DxBadRequestException("User does not have 'sub' access"));
                  return;
                } else {
                  LOGGER.debug("User has 'sub' access");
                  RoutingContextHelper.setItemMetaData(context, context.user().principal());
                  RoutingContextHelper.setPolicyExpiryAt(
                      context, principal.getString("expiryAt", null));
                  RoutingContextHelper.setProviderId(
                      context, principal.getString("ownerUserId", null));
                  context.next();
                  return;
                }
              })
          .onFailure(
              err -> {
                LOGGER.error("Error checking access: {}", err.getMessage());
                context.fail(new DxInternalServerErrorException("Error checking access"));
                return;
              });
    } else {
      LOGGER.debug("processing with control plane");
      String itemId;
      String bearerToken;
      try {
        itemId = RoutingContextHelper.getId(context);
        bearerToken = RoutingContextHelper.getToken(context);
      } catch (Exception e) {
        LOGGER.error("Error extracting request parameters", e);
        context.fail(e);
        return;
      }
      getApplicableFilter(itemId, bearerToken)
          .compose(
              result -> {
                RoutingContextHelper.setItemMetaData(context, result);
                RoutingContextHelper.setPolicyExpiryAt(context, result.getString("expiryAt", null));
                RoutingContextHelper.setProviderId(context, result.getString("ownerUserId", null));
                return hasAccess(result, "sub");
              })
          .onSuccess(
              sucesss -> {
                if (!sucesss) {
                  context.fail(new DxBadRequestException("User does not have 'sub' access"));
                  return;
                } else {
                  LOGGER.debug("User has 'sub' access");
                  context.next();
                  return;
                }
              })
          .onFailure(
              err -> {
                LOGGER.error("failed {}", err.getMessage());
                context.fail(err);
              });
    }
  }

  private Future<JsonObject> getApplicableFilter(String itemId, String bearerToken) {
    LOGGER.debug("Fetching item metadata for itemId: {}", itemId);
    Promise<JsonObject> promise = Promise.promise();
    HttpRequest<?> getRequest = webClient.getAbs(checkItemAndFilterUrl);

    getRequest
        .addQueryParam("id", itemId)
        .putHeader("Authorization", "Bearer " + bearerToken)
        .send()
        .onSuccess(
            resp -> {
              LOGGER.debug("Item metadata fetch response status: {}", resp.statusCode());
              if (resp.statusCode() == 200) {
                try {
                  JsonObject responseJson = resp.bodyAsJsonObject();
                  JsonObject resultObj = responseJson.getJsonArray("result").getJsonObject(0);
                  if (resultObj != null && !resultObj.isEmpty()) {
                    promise.complete(resultObj);
                  } else {
                    LOGGER.error("No response from control plane");
                    promise.fail(new DxBadRequestException("No response from control plane"));
                  }
                } catch (Exception e) {
                  LOGGER.error("Error in from control plane {}", e.getMessage());
                  promise.fail(
                      new DxInternalServerErrorException("Invalid response from control plane"));
                }
              } else {
                promise.fail(
                    new DxForbiddenNoAccessException(
                        "Access check failed " + resp.bodyAsJsonObject().getString("detail")));
              }
            })
        .onFailure(
            err ->
                promise.fail(
                    new DxInternalServerErrorException(
                        "Item metadata fetch failed: " + err.getMessage())));
    return promise.future();
  }
}
