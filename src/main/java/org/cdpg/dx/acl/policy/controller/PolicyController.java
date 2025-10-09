package org.cdpg.dx.acl.policy.controller;

import static org.cdpg.dx.aaa.asset.util.Constants.TYPE;
import static org.cdpg.dx.acl.accessRequest.config.Constants.APPLICATION_JSON;
import static org.cdpg.dx.acl.accessRequest.config.Constants.CONTENT_TYPE;
import static org.cdpg.dx.acl.accessRequest.config.Constants.CREATE_POLICY_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.DELETE_POLICY_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.DETAIL;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_POLICY_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.HEADER_X_CONTENT_TYPE_OPTIONS;
import static org.cdpg.dx.acl.accessRequest.config.Constants.TITLE;
import static org.cdpg.dx.acl.accessRequest.config.Constants.VERIFY_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.X_CONTENT_TYPE_OPTIONS_NOSNIFF;
import static org.cdpg.dx.common.HttpStatusCode.BAD_REQUEST;
import static org.cdpg.dx.common.ResponseUrn.BAD_REQUEST_URN;
import static org.cdpg.dx.common.ResponseUtil.generateResponse;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.Constants;
import org.cdpg.dx.acl.apiserver.ApdApiController;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.acl.policy.util.UserAccessHandler;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.ResponseUrn;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class PolicyController implements ApdApiController {
  private static final Logger LOGGER = LogManager.getLogger(PolicyController.class);
  private final PolicyService policyService;
  private final AuditingHandler auditingHandler;
  private final PostgresService postgresService;
  private final URNGenerator urnGenerator;
  private final JsonObject config;
  public PolicyController(PolicyService policyService,
                          PostgresService postgresService, AuditingHandler auditingHandler,
                          URNGenerator urnGenerator, JsonObject config) {
    this.policyService = policyService;
    this.postgresService = postgresService;
    this.auditingHandler = auditingHandler;
    this.urnGenerator = urnGenerator;
    this.config = config;
  }

  @Override
  public void register(RouterBuilder builder) {
    Handler<RoutingContext> providerAndOrgAdmin =
        AuthorizationHandler.forRoles(DxRole.PROVIDER, DxRole.ORG_ADMIN);
    Handler<RoutingContext> apiAccessHandler =
        AuthorizationHandler.forRoles(DxRole.CONSUMER, DxRole.PROVIDER,
            DxRole.DELEGATE);
    UserAccessHandler userAccessHandler = new UserAccessHandler(postgresService);

    builder.operation(CREATE_POLICY_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(providerAndOrgAdmin)
        .handler(userAccessHandler)
        .handler(this::handleCreatePolicy);

    builder.operation(GET_POLICY_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(apiAccessHandler)
        .handler(userAccessHandler)
        .handler(this::handleGetPolicies);

    builder.operation(DELETE_POLICY_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(providerAndOrgAdmin)
        .handler(userAccessHandler)
        .handler(this::handleDeletePolicy);

    builder.operation(VERIFY_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(providerAndOrgAdmin)
        .handler(userAccessHandler)
        .handler(this::verifyRequestHandler);
  }

  private void handleCreatePolicy(RoutingContext ctx) {
    LOGGER.info("Handling createPolicy request...");
    JsonObject request = ctx.body().asJsonObject();
    DxUser user;
    try {
      user = RoutingContextHelper.fromPrincipal(ctx);
    } catch (Exception e) {
      LOGGER.error("Error extracting user from token: {}", e.getMessage(), e);
      ctx.fail(new DxForbiddenException("Invalid user"));
      return;
    }
    // default expiry days
    request.put("defaultExpiryDays", config.getLong("defaultExpiryDays"));

    boolean isAdditionalInfoPresent = request.containsKey("additionalInfo");
    if (isAdditionalInfoPresent) {
      boolean isAnyValueNull =
          request.getJsonObject("additionalInfo").getMap().values().stream()
              .anyMatch(Objects::isNull);
      if (isAnyValueNull) {
        JsonObject failureMessage =
            new JsonObject()
                .put(Constants.TYPE, BAD_REQUEST.getValue())
                .put(Constants.TITLE, BAD_REQUEST_URN.getUrn())
                .put(Constants.DETAIL, "Policy cannot be created, as additionalInfo contains a null value");
        handleFailureResponse(ctx, failureMessage.encode());
      }
    }
    LOGGER.debug("request: " + request);
    JsonArray policyList = request.getJsonArray("request");
    List<CreatePolicyRequest> requests =
        CreatePolicyRequest.jsonArrayToList(policyList, request.getLong("defaultExpiryDays"));

    policyService.createPolicy(requests, user)
        .onSuccess(ar -> {
        LOGGER.info("Policy created successfully ");
        ResponseBuilder.sendSuccess(ctx, "Policy created successfully", urnGenerator);
      }).onFailure(err-> {
        LOGGER.error("Policy could not be created");
        handleFailureResponse(ctx, err.getMessage());
      });
  }

  private void handleGetPolicies(RoutingContext ctx) {
    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    policyService
        .getPolicy(user)
        .onSuccess(res -> {
          JsonArray resultArray = new JsonArray();
          res.forEach(dto -> resultArray.add(dto.toJson()));
          ResponseBuilder.sendSuccess(ctx, resultArray, urnGenerator);
        })
        .onFailure(err -> {
          LOGGER.debug("Failed to get policies for user {} cause {}", user.sub(),
              err.getLocalizedMessage());
          handleFailureResponse(ctx, err.getMessage());
        });
  }

  private void handleDeletePolicy(RoutingContext ctx) {
    JsonObject policy = ctx.body().asJsonObject();
    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    policyService
        .deletePolicy(policy, user)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                LOGGER.info("Delete policy succeeded");
                ResponseBuilder.sendSuccess(ctx, "Policy deleted successfully", urnGenerator);
              } else {
                LOGGER.error("Delete policy failed : {} ", handler.cause().getMessage());
                handleFailureResponse(ctx, handler.cause().getMessage());
              }
            });
  }

  private void verifyRequestHandler(RoutingContext ctx) {
    JsonObject request = ctx.body().asJsonObject();
    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    try {
      UUID ownerId = UUID.fromString(request.getJsonObject("owner").getString("id"));
      String userEmail = request.getJsonObject("user").getString("email");
      UUID itemId = UUID.fromString(request.getJsonObject("item").getString("itemId"));
      ItemType itemType =
          ItemType.fromTypeValue(request.getJsonObject("item").getString("itemType").toUpperCase());
      policyService
          .initiateVerifyPolicy(ownerId, userEmail, itemId, itemType, user)
          .onComplete(
              handler -> {
                if (handler.succeeded()) {
                  LOGGER.info("Policy verified successfully ");
                  ResponseBuilder.sendSuccess(ctx, handler.result().toJson(), urnGenerator);
                } else {
                  LOGGER.error("Policy could not be verified {}", handler.cause().getMessage());
                  handleFailureResponse(ctx, handler.cause().getMessage());
                }
              });
    } catch (Exception e) {
      LOGGER.error("Error in verifyPolicy: {}", e.getMessage());
      ctx.fail(e);
    }
  }

  /**
   * Handles HTTP Success response from the server
   *
   * @param response   HttpServerResponse object
   * @param statusCode statusCode to respond with
   * @param result     respective result returned from the service
   */
  private void handleSuccessResponse(HttpServerResponse response, int statusCode, String result) {
    response.putHeader(HEADER_X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_NOSNIFF);
    response.putHeader(CONTENT_TYPE, APPLICATION_JSON).setStatusCode(statusCode).end(result);
  }

  /**
   * Handles Failed HTTP Response
   *
   * @param routingContext Routing context object
   * @param failureMessage Failure message for response
   */
  private void handleFailureResponse(RoutingContext routingContext, String failureMessage) {
    String detail;
    HttpServerResponse response = routingContext.response();
    LOGGER.debug("Failure Message : {} ", failureMessage);

    try {
      JsonObject jsonObject = new JsonObject(failureMessage);
      int type = jsonObject.getInteger(TYPE);
      String title = jsonObject.getString(TITLE);
      detail = jsonObject.getString(DETAIL);

      HttpStatusCode status = HttpStatusCode.getByValue(type);

      String urn = urnGenerator.generateUrn(status.getPath());

      if (jsonObject.getString(DETAIL) != null) {
        detail = jsonObject.getString(DETAIL);
        response
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
            .setStatusCode(type)
            .end(generateResponse(status, String.valueOf(urn), detail).toString());
      } else {
        response
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
            .setStatusCode(type)
            .end(generateResponse(status, String.valueOf(urn)).toString());
      }

    } catch (DecodeException exception) {
      LOGGER.error("Error : Expecting JSON from backend service [ jsonFormattingException ] ");
      handleResponse(response, BAD_REQUEST, ResponseUrn.BACKING_SERVICE_FORMAT_URN);
    }
  }

  private void handleResponse(
      HttpServerResponse response, HttpStatusCode statusCode, ResponseUrn urn) {
    handleResponse(response, statusCode, urn, statusCode.getDescription());
  }

  private void handleResponse(
      HttpServerResponse response,
      HttpStatusCode statusCode,
      ResponseUrn urn,
      String failureMessage) {
    response
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .setStatusCode(statusCode.getValue())
        .end(generateResponse(statusCode, String.valueOf(urn), failureMessage).toString());
  }
}
