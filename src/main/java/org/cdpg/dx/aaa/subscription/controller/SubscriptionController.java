package org.cdpg.dx.aaa.subscription.controller;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.HEADER_ALLOW_ORIGIN;
import static org.cdpg.dx.aaa.subscription.util.SubscriptionConstants.*;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.subscription.service.SubscriptionService;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.common.validations.idhandler.GetIdFromBodyHandler;
import org.cdpg.dx.common.validations.itemcheck.SubscriptionAuthorizationHandler;

public class SubscriptionController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(SubscriptionController.class);
  private final SubscriptionService subscriptionService;
  Handler<RoutingContext> roleAllowed =
      AuthorizationHandler.forRoles(DxRole.CONSUMER_DELEGATE, DxRole.CONSUMER);
  SubscriptionAuthorizationHandler subscriptionAuthorizationHandler;

  public SubscriptionController(
      SubscriptionService subscriptionService,
      URNGenerator urnGenerator,
      String controlPlaneDomain) {
    this.subscriptionService = subscriptionService;
    this.subscriptionAuthorizationHandler =
        new SubscriptionAuthorizationHandler(controlPlaneDomain);
  }

  private static int parseIntOrDefault(List<String> values, int defaultValue) {
    if (values == null || values.isEmpty()) return defaultValue;
    try {
      return Integer.parseInt(values.get(0));
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /*private static LocalDateTime parseAndValidateFutureTimeWithPolicy(
      String expiryAt, String policyExpiry) {
    LocalDateTime now = LocalDateTime.now();
    if (policyExpiry == null || policyExpiry.isBlank()) {

      if (expiryAt == null || expiryAt.isBlank()) {
        // both null → default expiry = now + 1 year
        return now.plusYears(1);
      }
      LocalDateTime userExpiry;
      try {
        userExpiry = LocalDateTime.parse(expiryAt);
      } catch (DateTimeParseException e) {
        throw new DxValidationException("expiryAt has invalid format, expected ISO format");
      }

      if (userExpiry.isAfter(now)) {
        return userExpiry;
      } else {
        throw new DxValidationException("expiryAt must be a future time");
      }
    }

    LocalDateTime policyTime;
    try {
      policyTime = LocalDateTime.parse(policyExpiry);
    } catch (DateTimeParseException e) {
      throw new DxValidationException("policyExpiry has invalid format, expected ISO format");
    }

    // policy must be in the future too
    if (!policyTime.isAfter(now)) {
      throw new DxValidationException("policyExpiry must be a future time");
    }

    // expiryAt is NULL → expiryAt = policyExpiry
    if (expiryAt == null || expiryAt.isBlank()) {
      return policyTime;
    }

    // expiryAt is provided → parse & compare
    LocalDateTime userExpiry;
    try {
      userExpiry = LocalDateTime.parse(expiryAt);
    } catch (DateTimeParseException e) {
      throw new DxValidationException("expiryAt has invalid format, expected ISO format");
    }

    // must be in future
    if (!userExpiry.isAfter(now)) {
      throw new DxValidationException("expiryAt must be a future time");
    }

    // must NOT exceed policy
    if (userExpiry.isAfter(policyTime)) {
      throw new DxValidationException("expiryAt cannot be greater than policyExpiry");
    }

    return userExpiry;
  }*/

  private static LocalDateTime parseAndValidateFutureTimeWithPolicy2(
      String expiryAt, String policyExpiry) {
    ZonedDateTime now = ZonedDateTime.now();
    if (policyExpiry == null || policyExpiry.isBlank()) {

      if (expiryAt == null || expiryAt.isBlank()) {
        // both null → default expiry = now + 1 year
        return now.plusYears(1).toLocalDateTime();
      }
      ZonedDateTime userExpiry;
      try {
        userExpiry = ZonedDateTime.parse(expiryAt);
      } catch (DateTimeParseException e) {
        throw new DxValidationException("expiryAt has invalid format, expected ISO8601 format");
      }

      if (userExpiry.isAfter(now)) {
        return userExpiry.toLocalDateTime();
      } else {
        throw new DxValidationException("expiryAt must be a future time");
      }
    }

    LocalDateTime policyTime;
    try {
      policyTime = LocalDateTime.parse(policyExpiry);
    } catch (DateTimeParseException e) {
      throw new DxValidationException("policyExpiry has invalid format, expected format");
    }

    // policy must be in the future too
    if (!policyTime.isAfter(now.toLocalDateTime())) {
      throw new DxValidationException("policyExpiry must be a future time");
    }

    // expiryAt is NULL → expiryAt = policyExpiry
    if (expiryAt == null || expiryAt.isBlank()) {
      return policyTime;
    }

    // expiryAt is provided → parse & compare
    ZonedDateTime userExpiry;
    try {
      userExpiry = ZonedDateTime.parse(expiryAt);
    } catch (DateTimeParseException e) {
      throw new DxValidationException("expiryAt has invalid format, expected ISO format");
    }

    // must be in future
    if (!userExpiry.isAfter(now)) {
      throw new DxValidationException("expiryAt must be a future time");
    }

    // must NOT exceed policy
    if (userExpiry.toLocalDateTime().isAfter(policyTime)) {
      throw new DxValidationException("expiryAt cannot be greater than policyExpiry");
    }

    return userExpiry.toLocalDateTime();
  }

  @Override
  public void register(RouterBuilder builder) {
    GetIdFromBodyHandler getIdFromBodyHandler = new GetIdFromBodyHandler();
    builder.operation(DELETE_SUBSCRIPTION).handler(roleAllowed).handler(this::deleteSubscription);
    builder
        .operation(GET_BY_ID_SUBSCRIPTION)
        .handler(roleAllowed)
        .handler(this::getSubscriptionById);
    builder.operation(GET_ALL_SUBSCRIPTION).handler(roleAllowed).handler(this::getAllSubscriptions);
    builder
        .operation(UPDATE_SUBSCRIPTION)
        .handler(getIdFromBodyHandler)
        .handler(subscriptionAuthorizationHandler)
        .handler(roleAllowed)
        .handler(this::updateSubscription);
    builder
        .operation(CREATE_SUBSCRIPTION)
        .handler(getIdFromBodyHandler)
        .handler(subscriptionAuthorizationHandler)
        .handler(roleAllowed)
        .handler(this::createSubscription);
  }

  private void createSubscription(RoutingContext routingContext) {
    JsonObject requestBody = routingContext.body().asJsonObject();
    String userId = routingContext.user().subject();
    String policyAt = RoutingContextHelper.getPolicyExpiryAt(routingContext);
    String entitiesId = requestBody.getJsonArray("entities").getString(0);
    String subscriptionName =
        requestBody.getString(
            SUBSCRIPTION_NAME,
            "sub-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    String subscriptionId = requestBody.getString("id", UUID.randomUUID().toString());
    LocalDateTime expiryAt =
        parseAndValidateFutureTimeWithPolicy2(requestBody.getString("expiryAt"), policyAt);
    LOGGER.debug("expiryAt {}", expiryAt);
    String providerId = RoutingContextHelper.getProviderId(routingContext);
    subscriptionService
        .createSubscription(
            userId,
            UUID.fromString(subscriptionId),
            subscriptionName,
            entitiesId,
            expiryAt,
            providerId)
        .onSuccess(
            v ->
                routingContext
                    .response()
                    .putHeader("Content-Type", "application/json")
                    .putHeader(HEADER_ALLOW_ORIGIN, "*")
                    .putHeader("Location", v.subscriptionId())
                    .putHeader(
                        "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
                    .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
                    .setStatusCode(201)
                    .end(v.toJson().encode()))
        .onFailure(routingContext::fail);
  }

  private void updateSubscription(RoutingContext routingContext) {
    LOGGER.debug("updateSubscription called");
    HttpServerRequest request = routingContext.request();
    String subsId = request.getParam(SUBSCRIPTION_ID);
    JsonObject requestJson = routingContext.body().asJsonObject();
    String policyAt = RoutingContextHelper.getPolicyExpiryAt(routingContext);
    String entities = requestJson.getJsonArray("entities").getString(0);

    subscriptionService
        .updateSubscription(
            entities,
            subsId,
            parseAndValidateFutureTimeWithPolicy2(requestJson.getString("expiryAt"), policyAt))
        .onSuccess(
            v ->
                routingContext
                    .response()
                    .putHeader("Content-Type", "application/json")
                    .putHeader(HEADER_ALLOW_ORIGIN, "*")
                    .putHeader(
                        "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
                    .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
                    .setStatusCode(204)
                    .end())
        .onFailure(routingContext::fail);
  }

  private void getAllSubscriptions(RoutingContext routingContext) {
    LOGGER.debug("getAllSubscriptions called");
    int limit = parseIntOrDefault(routingContext.queryParam("limit"), 100);
    int offset = parseIntOrDefault(routingContext.queryParam("offset"), 0);
    String userId = routingContext.user().subject();
    subscriptionService
        .getAllSubscriptions(userId, limit, offset)
        .onSuccess(
            getResult ->
                routingContext
                    .response()
                    .putHeader("Content-Type", "application/json")
                    .putHeader(HEADER_ALLOW_ORIGIN, "*")
                    .putHeader(
                        "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
                    .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
                    .putHeader(NGSILD_RESULTS_COUNT, String.valueOf(getResult.count()))
                    .putHeader(NGSILD_LIMIT, String.valueOf(limit))
                    .putHeader(NGSILD_OFFSET, String.valueOf(offset))
                    .setStatusCode(200)
                    .end(getResult.withoutTotalCount().toString()))
        .onFailure(routingContext::fail);
  }

  private void getSubscriptionById(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    String subsId = request.getParam(SUBSCRIPTION_ID);
    String userId = routingContext.user().subject();
    LOGGER.info("subscriptionId {}", subsId);
    subscriptionService
        .getSubscriptionById(subsId, userId)
        .onSuccess(
            getResult ->
                routingContext
                    .response()
                    .putHeader("Content-Type", "application/json")
                    .putHeader(HEADER_ALLOW_ORIGIN, "*")
                    .putHeader(
                        "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
                    .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
                    .setStatusCode(200)
                    .end(getResult.jsonArray().encode()))
        .onFailure(routingContext::fail);
  }

  private void deleteSubscription(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    String subsId = request.getParam(SUBSCRIPTION_ID);

    String userId = routingContext.user().subject();
    subscriptionService
        .deleteSubscription(subsId, userId)
        .onSuccess(
            v ->
                routingContext
                    .response()
                    .putHeader("Content-Type", "application/json")
                    .putHeader(HEADER_ALLOW_ORIGIN, "*")
                    .putHeader(
                        "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
                    .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
                    .setStatusCode(204)
                    .end())
        .onFailure(routingContext::fail);
  }
}
