package org.cdpg.dx.acl.policy.controller;

import static org.cdpg.dx.aaa.asset.util.Constants.TYPE;
import static org.cdpg.dx.acl.accessRequest.config.Constants.APPLICATION_JSON;
import static org.cdpg.dx.acl.accessRequest.config.Constants.CONTENT_TYPE;
import static org.cdpg.dx.acl.accessRequest.config.Constants.CREATE_POLICY_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.DELETE_POLICY_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.DETAIL;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_POLICIES_CONSUMER_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_POLICIES_FOR_COS_ADMIN_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_POLICIES_FOR_ORG_ADMIN_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_POLICIES_PROVIDER_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_POLICY_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.HEADER_X_CONTENT_TYPE_OPTIONS;
import static org.cdpg.dx.acl.accessRequest.config.Constants.ID;
import static org.cdpg.dx.acl.accessRequest.config.Constants.TITLE;
import static org.cdpg.dx.acl.accessRequest.config.Constants.VERIFY_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.X_CONTENT_TYPE_OPTIONS_NOSNIFF;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ASSET_ORGANIZATION_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CREATED_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_EXPIRY_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_UPDATED_AT;
import static org.cdpg.dx.acl.policy.util.Constants.API_TO_DB_MAP;
import static org.cdpg.dx.common.HttpStatusCode.BAD_REQUEST;
import static org.cdpg.dx.common.ResponseUrn.BAD_REQUEST_URN;
import static org.cdpg.dx.common.ResponseUtil.generateResponse;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.Constants;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.acl.policy.util.UserAccessHandler;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.v2.handler.AuthenticationHandler;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.handler.ScopeRule;
import org.cdpg.dx.auth.v2.model.Scopes;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.ResponseUrn;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class PolicyController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(PolicyController.class);
  private final PolicyService policyService;
  private final AuditingHandler auditingHandler;
  private final PostgresService postgresService;
  private final KeycloakUserService keycloakUserService;
  private final URNGenerator urnGenerator;
  private final JsonObject config;
  private final AuthenticationHandler authenticationV2;
  private final AuthorizationHandler authorizationV2;

  public PolicyController(
      PolicyService policyService,
      PostgresService postgresService,
      AuditingHandler auditingHandler,
      KeycloakUserService keycloakUserService,
      URNGenerator urnGenerator,
      JsonObject config,
      AuthenticationHandler authenticationV2,
      AuthorizationHandler authorizationV2) {
    this.policyService = policyService;
    this.postgresService = postgresService;
    this.auditingHandler = auditingHandler;
    this.keycloakUserService = keycloakUserService;
    this.urnGenerator = urnGenerator;
    this.config = config;
    this.authenticationV2 = authenticationV2;
    this.authorizationV2 = authorizationV2;
  }

  @Override
  public void register(RouterBuilder builder) {
    Handler<RoutingContext> selfOrConsumerAccess =
        authorizationV2.forScopes(Scopes.DATA_ACCESS, Scopes.OWN_ASSET_MANAGEMENT);
    Handler<RoutingContext> verifyAccess =
        authorizationV2.forScopes(
            Scopes.DATA_ACCESS, Scopes.OWN_ASSET_MANAGEMENT, Scopes.ORG_ASSET_MANAGEMENT);
    Handler<RoutingContext> policyAdminAccess =
        authorizationV2.forScopesWithContext(
            ScopeRule.self(Scopes.OWN_ASSET_MANAGEMENT),
            ScopeRule.org(Scopes.ORG_ASSET_MANAGEMENT));
    Handler<RoutingContext> cosAdminAccessHandler = authorizationV2.forScopes(Scopes.ASSET_MANAGEMENT);
    Handler<RoutingContext> orgAdminAccessHandler = authorizationV2.forScopes(Scopes.ORG_ASSET_MANAGEMENT);
    Handler<RoutingContext> providerAndOrgAdmin =
        authorizationV2.forScopesWithContext(
            ScopeRule.self(Scopes.OWN_ASSET_MANAGEMENT),
            ScopeRule.org(Scopes.ORG_ASSET_MANAGEMENT));
    Handler<RoutingContext> apiAccessHandler =
        authorizationV2.forScopesWithContext(
            ScopeRule.self(Scopes.OWN_ASSET_MANAGEMENT), ScopeRule.self(Scopes.DATA_ACCESS));
    Handler<RoutingContext> apiAccessVerifyApiRole =
        authorizationV2.forScopesWithContext(
            ScopeRule.self(Scopes.OWN_ASSET_MANAGEMENT), ScopeRule.self(Scopes.DATA_ACCESS),
            ScopeRule.org(Scopes.ORG_ASSET_MANAGEMENT));
    UserAccessHandler userAccessHandler = new UserAccessHandler(postgresService,
        keycloakUserService);

    builder.operation(CREATE_POLICY_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(authenticationV2)
        .handler(policyAdminAccess)
        .handler(userAccessHandler)
        .handler(this::handleCreatePolicy);

//    builder.operation(GET_POLICY_API)
//        .handler(auditingHandler::handleApiAudit)
//        .handler(authenticationV2)
//        .handler(selfOrConsumerAccess)
//        .handler(userAccessHandler)
//        .handler(this::handleGetPolicies);

    builder
        .operation(GET_POLICIES_CONSUMER_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(this::getConsumerPoliciesHandler);

    builder
        .operation(GET_POLICIES_PROVIDER_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(authenticationV2)
        .handler(providerAndOrgAdmin)
        .handler(this::getPoliciesHandler);

    builder
        .operation(GET_POLICIES_FOR_ORG_ADMIN_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(authenticationV2)
        .handler(orgAdminAccessHandler)
        .handler(this::getOrganizationPoliciesHandler);

    builder
        .operation(GET_POLICIES_FOR_COS_ADMIN_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(authenticationV2)
        .handler(cosAdminAccessHandler)
        .handler(this::getPlatformPoliciesHandler);

    builder.operation(DELETE_POLICY_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(authenticationV2)
        .handler(policyAdminAccess)
        .handler(userAccessHandler)
        .handler(this::handleDeletePolicy);

    builder.operation(VERIFY_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(authenticationV2)
        .handler(verifyAccess)
        .handler(userAccessHandler)
        .handler(this::verifyRequestHandler);
  }

  private void getPlatformPoliciesHandler(RoutingContext ctx) {
    LOGGER.info("Handling getPlatformPoliciesHandler request...");
    User user = ctx.user();

    Map<String, String> allowedFilters =
        Map.of("status", DB_STATUS, "organizationId", DB_ASSET_ORGANIZATION_ID);
    Set<String> allowedTimeFields = Set.of(DB_CREATED_AT, DB_UPDATED_AT, DB_EXPIRY_AT);
    Set<String> allowedSortFields = API_TO_DB_MAP.keySet();

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(allowedFilters)
            .apiToDbMap(API_TO_DB_MAP)
            .allowedTimeFields(allowedTimeFields)
            .defaultTimeField(DB_CREATED_AT)
            .defaultSort(DB_UPDATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(allowedSortFields)
            .build();

    LOGGER.info("PaginatedRequest getPlatformAccessRequestHandler for cos admin :  {}", request);

    policyService
        .listPolicies(request)
        .compose(policyService::enrichPolicyRequestsWithItemDetails)
        .compose(policyService::enrichPolicyRequestsWithUserInfo)
        .onSuccess(
            pagedResult -> {
              LOGGER.info(
                  "Successfully fetched access requests for cos admin user: {}", user.subject());
              ResponseBuilder.sendSuccess(
                  ctx,
                  pagedResult.data().stream().map(PolicyDto::toJson).collect(Collectors.toList()),
                  pagedResult.paginationInfo(),
                  urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error("Error fetching access requests: {}", err.getMessage(), err);
              ctx.fail(err);
            });
  }

  private void getOrganizationPoliciesHandler(RoutingContext ctx) {
    LOGGER.info("Handling getOrganizationPoliciesHandler request...");
    User user = ctx.user();

    String organizationId = RoutingContextHelper.fromPrincipal(ctx).organisationId();
    Map<String, String> allowedFilters =
        Map.of("status", DB_STATUS, "organizationId", DB_ASSET_ORGANIZATION_ID);
    Map<String, Object> additionalFilters = Map.of(DB_ASSET_ORGANIZATION_ID, organizationId);
    Set<String> allowedTimeFields = Set.of(DB_CREATED_AT, DB_UPDATED_AT, DB_EXPIRY_AT);
    Set<String> allowedSortFields = API_TO_DB_MAP.keySet();

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(allowedFilters)
            .apiToDbMap(API_TO_DB_MAP)
            .additionalFilters(additionalFilters)
            .allowedTimeFields(allowedTimeFields)
            .defaultTimeField(DB_CREATED_AT)
            .defaultSort(DB_UPDATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(allowedSortFields)
            .build();

    LOGGER.info(
        "PaginatedRequest getOrganizationAccessRequestHandler for org admin :  {}", request);

    policyService
        .listPolicies(request)
        .compose(policyService::enrichPolicyRequestsWithItemDetails)
        .compose(policyService::enrichPolicyRequestsWithUserInfo)
        .onSuccess(
            pagedResult -> {
              LOGGER.info(
                  "Successfully fetched access requests for org admin user: {}", user.subject());
              ResponseBuilder.sendSuccess(
                  ctx,
                  pagedResult.data().stream().map(PolicyDto::toJson).collect(Collectors.toList()),
                  pagedResult.paginationInfo(),
                  urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error("Error fetching access requests: {}", err.getMessage(), err);
              ctx.fail(err);
            });
  }

  private void getPoliciesHandler(RoutingContext ctx) {
    LOGGER.info("Handling getPolicies request...");
    User user = ctx.user();

    Map<String, String> allowedFilters =
        Map.of("status", DB_STATUS, "organizationId", DB_ASSET_ORGANIZATION_ID);
    Map<String, Object> additionalFilters = Map.of("owner_id", user.subject());
    Set<String> allowedTimeFields = Set.of(DB_CREATED_AT, DB_UPDATED_AT, DB_EXPIRY_AT);
    Set<String> allowedSortFields = API_TO_DB_MAP.keySet();

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(allowedFilters)
            .apiToDbMap(API_TO_DB_MAP)
            .additionalFilters(additionalFilters)
            .allowedTimeFields(allowedTimeFields)
            .defaultTimeField("created_at")
            .defaultSort("updated_at", DEFAULT_SORTING_ORDER)
            .allowedSortFields(allowedSortFields)
            .build();

    LOGGER.info("PaginatedRequest created getAccessRequest for Provider :  {}", request);

    policyService
        .listPolicies(request)
        .compose(policyService::enrichPolicyRequestsWithItemDetails)
        .compose(policyService::enrichPolicyRequestsWithUserInfo)
        .onSuccess(
            pagedResult -> {
              LOGGER.info(
                  "Successfully fetched access requests for provider user: {}", user.subject());
              ResponseBuilder.sendSuccess(
                  ctx,
                  pagedResult.data().stream().map(PolicyDto::toJson).collect(Collectors.toList()),
                  pagedResult.paginationInfo(),
                  urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error("Error fetching access requests: {}", err.getMessage(), err);
              ctx.fail(err);
            });
  }

  private void handleCreatePolicy(RoutingContext ctx) {
    LOGGER.info("Handling createPolicy request...");
    JsonObject request = ctx.body().asJsonObject();
    DxUser user;
    try {
      user = RoutingContextHelper.getDxUser(ctx);
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
                .put(
                    Constants.DETAIL,
                    "Policy cannot be created, as additionalInfo contains a null value");
        handleFailureResponse(ctx, failureMessage.encode());
      }
    }
    LOGGER.debug("request: {}", request);
    JsonArray policyList = request.getJsonArray("request");
    List<CreatePolicyRequest> requests =
        CreatePolicyRequest.jsonArrayToList(policyList, request.getLong("defaultExpiryDays"));

    policyService
        .createPolicy(requests, user)
        .onSuccess(
            ar -> {
              LOGGER.info("Policy created successfully ");
              ResponseBuilder.sendSuccess(ctx, "Policy created successfully", urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error("Policy could not be created");
              handleFailureResponse(ctx, err.getMessage());
            });
  }

  private void handleGetPolicies(RoutingContext ctx) {
    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    policyService
        .getPolicy(user)
        .onSuccess(
            res -> {
              JsonArray resultArray = new JsonArray();
              res.forEach(dto -> resultArray.add(dto.toJson()));
              ResponseBuilder.sendSuccess(ctx, resultArray, urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.debug(
                  "Failed to get policies for user {} cause {}",
                  user.sub(),
                  err.getLocalizedMessage());
              handleFailureResponse(ctx, err.getMessage());
            });
  }

  private void handleDeletePolicy(RoutingContext ctx) {
    String policyId = ctx.queryParams().get(ID);
    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    policyService
        .deActivatePolicy(policyId, user)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                LOGGER.info("Deactivating policy succeeded");
                ResponseBuilder.sendSuccess(ctx, "Policy deactivated successfully", urnGenerator);
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
      String userId = request.getJsonObject("user").getString("id");
      UUID itemId = UUID.fromString(request.getJsonObject("item").getString("itemId"));
      ItemType itemType =
          ItemType.fromTypeValue(request.getJsonObject("item").getString("itemType").toUpperCase());
      policyService
          .initiateVerifyPolicy(ownerId, userId, itemId, itemType, user)
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

  private void getConsumerPoliciesHandler(RoutingContext ctx) {
    LOGGER.info("Handling getConsumerPolicies request...");
    User user = ctx.user();

    Map<String, String> allowedFilters =
        Map.of("status", DB_STATUS, "organizationId", DB_ASSET_ORGANIZATION_ID);
    Map<String, Object> additionalFilters = Map.of("consumer_id", user.subject());
    Set<String> allowedTimeFields = Set.of(DB_CREATED_AT, DB_UPDATED_AT, DB_EXPIRY_AT);
    Set<String> allowedSortFields = API_TO_DB_MAP.keySet();

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(allowedFilters)
            .apiToDbMap(API_TO_DB_MAP)
            .additionalFilters(additionalFilters)
            .allowedTimeFields(allowedTimeFields)
            .defaultTimeField("created_at")
            .defaultSort("updated_at", DEFAULT_SORTING_ORDER)
            .allowedSortFields(allowedSortFields)
            .build();

    LOGGER.info("PaginatedRequest created for getActivityLogForConsumer:  {}", request);

    policyService
        .listPolicies(request)
        .compose(policyService::enrichPolicyRequestsWithItemDetails)
        .compose(policyService::enrichPolicyRequestsWithUserInfo)
        .onSuccess(
            pagedResult -> {
              LOGGER.info("Successfully fetched access requests for user: {}", user.subject());
              ResponseBuilder.sendSuccess(
                  ctx,
                  pagedResult.data().stream()
                      .map(PolicyDto::toJson) // call toJson on each object
                      .collect(Collectors.toList()),
                  pagedResult.paginationInfo(),
                  urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error("Error fetching access requests: {}", err.getMessage(), err);
              ctx.fail(err);
            });
  }

  /**
   * Handles HTTP Success response from the server
   *
   * @param response HttpServerResponse object
   * @param statusCode statusCode to respond with
   * @param result respective result returned from the service
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
