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

  private static JsonObject normalizeItem(JsonObject input) {
    if (input == null) {
      return new JsonObject();
    }
    Object resultObj = input.getValue("result");
    if (resultObj instanceof JsonArray resultArray
        && !resultArray.isEmpty()
        && resultArray.getValue(0) instanceof JsonObject) {
      return resultArray.getJsonObject(0);
    }
    return input;
  }

  private static boolean accessArrayHas(JsonArray accessArray, String accessType) {
    if (accessArray == null || accessArray.isEmpty() || accessType == null) {
      return false;
    }
    for (int i = 0; i < accessArray.size(); i++) {
      Object entry = accessArray.getValue(i);
      if (entry instanceof String s) {
        if (s.equalsIgnoreCase(accessType)) {
          return true;
        }
      } else if (entry instanceof JsonObject obj) {
        String type = obj.getString("accessType", null);
        if (type != null && type.equalsIgnoreCase(accessType)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean accessArrayHasAny(JsonArray accessArray, JsonArray allowedTypes) {
    if (accessArray == null
        || accessArray.isEmpty()
        || allowedTypes == null
        || allowedTypes.isEmpty()) {
      return false;
    }
    for (int i = 0; i < allowedTypes.size(); i++) {
      String type = allowedTypes.getString(i);
      if (type != null && accessArrayHas(accessArray, type)) {
        return true;
      }
    }
    return false;
  }

  private static String getPolicyExpiryAt(JsonObject principal, String accessType) {
    JsonObject item = normalizeItem(principal);
    String directExpiry = item.getString("expiryAt", null);
    if (directExpiry != null) {
      return directExpiry;
    }

    JsonArray policies = item.getJsonArray("policies");
    if (policies == null || policies.isEmpty()) {
      return null;
    }

    String fallbackExpiry = null;
    for (int i = 0; i < policies.size(); i++) {
      JsonObject policy = policies.getJsonObject(i);
      if (policy == null) {
        continue;
      }
      String expiry = policy.getString("expiryAt", null);
      if (fallbackExpiry == null && expiry != null) {
        fallbackExpiry = expiry;
      }
      if (accessType == null) {
        continue;
      }
      JsonObject policyCons = policy.getJsonObject("cons");
      if (policyCons == null) {
        continue;
      }
      JsonArray accessArray = policyCons.getJsonArray("access");
      if (accessArrayHas(accessArray, accessType)) {
        return expiry;
      }
    }
    return fallbackExpiry;
  }

  private static boolean isPublicAccess(JsonObject item) {
    String accessPolicy = item.getString("accessPolicy", "");
    return accessPolicy.equalsIgnoreCase("open") || accessPolicy.equalsIgnoreCase("public");
  }

  private Future<Boolean> hasAccess(JsonObject principal, JsonArray allowedTypes) {
    Promise<Boolean> promise = Promise.promise();

    JsonObject item = normalizeItem(principal);

    JsonArray policies = item.getJsonArray("policies");
    if (policies != null && !policies.isEmpty()) {
      for (int i = 0; i < policies.size(); i++) {
        JsonObject policy = policies.getJsonObject(i);
        if (policy == null) {
          continue;
        }
        JsonObject policyCons = policy.getJsonObject("cons");
        if (policyCons == null) {
          continue;
        }
        JsonArray accessArray = policyCons.getJsonArray("access");
        if (accessArrayHasAny(accessArray, allowedTypes)) {
          promise.complete(true);
          return promise.future();
        }
      }
      promise.complete(false);
      return promise.future();
    }

    promise.fail("policies not found");
    return promise.future();
  }

  @Override
  public void handle(RoutingContext context) {
    LOGGER.info("Starting SubscriptionAuthorizationHandler");

    JsonObject principal = context.user().principal();
    JsonArray allowedAccessTypes = new JsonArray().add("sub").add("file").add("api");
    if (principal.containsKey("policies") || principal.containsKey("result")) {
      LOGGER.debug("Processing access token");

      JsonObject item = normalizeItem(principal);
      if (isPublicAccess(item)) {
        LOGGER.debug("Public access policy, skipping subscription check");
        RoutingContextHelper.setItemMetaData(context, item);
        RoutingContextHelper.setPolicyExpiryAt(context, getPolicyExpiryAt(item, "sub"));
        RoutingContextHelper.setProviderId(context, item.getString("ownerUserId", null));
        context.next();
        return;
      }

      hasAccess(principal, allowedAccessTypes)
          .onSuccess(
              access -> {
                if (!access) {
                  context.fail(new DxBadRequestException("User does not have required access"));
                  return;
                } else {
                  LOGGER.debug("User has required access");
                  RoutingContextHelper.setItemMetaData(context, item);
                  RoutingContextHelper.setPolicyExpiryAt(
                      context, getPolicyExpiryAt(principal, "sub"));
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
        bearerToken = RoutingContextHelper.getTokenOrThrow(context);
      } catch (Exception e) {
        LOGGER.error("Error extracting request parameters", e);
        context.fail(e);
        return;
      }
      getApplicableFilter(itemId, bearerToken)
          .compose(
              result -> {
                if (isPublicAccess(result)) {
                  LOGGER.debug("Public access policy, skipping subscription check");
                  RoutingContextHelper.setItemMetaData(context, result);
                  RoutingContextHelper.setPolicyExpiryAt(context, getPolicyExpiryAt(result, "sub"));
                  RoutingContextHelper.setProviderId(
                      context, result.getString("ownerUserId", null));
                  return Future.succeededFuture(true);
                } else {
                  RoutingContextHelper.setItemMetaData(context, result);
                  RoutingContextHelper.setPolicyExpiryAt(context, getPolicyExpiryAt(result, "sub"));
                  RoutingContextHelper.setProviderId(
                      context, result.getString("ownerUserId", null));
                  return hasAccess(result, allowedAccessTypes);
                }
              })
          .onSuccess(
              sucesss -> {
                if (!sucesss) {
                  context.fail(new DxBadRequestException("User does not have required access"));
                  return;
                } else {
                  LOGGER.debug("User has required access");
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
