package org.cdpg.dx.acl.accessRequest.controller;

import static org.cdpg.dx.acl.accessRequest.config.Constants.CHECK_ACCESS_REQUEST_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.CREATE_ACCESS_REQUEST_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_ACCESS_REQUEST_CONSUMER_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_ACCESS_REQUEST_FOR_COS_ADMIN_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_ACCESS_REQUEST_FOR_ORG_ADMIN_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.GET_ACCESS_REQUEST_PROVIDER_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.UPDATE_ACCESS_REQUEST_API;
import static org.cdpg.dx.acl.accessRequest.config.Constants.WITHDRAW_ACCESS_REQUEST_API_FOR_CONSUMER;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ASSET_ORGANIZATION_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ASSET_TYPE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CREATED_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_EXPIRY_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_UPDATED_AT;
import static org.cdpg.dx.acl.accessRequest.util.Constants.API_TO_DB_MAP;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.dao.model.Status;
import org.cdpg.dx.acl.accessRequest.model.AccessRequestAuditOperation;
import org.cdpg.dx.acl.accessRequest.service.AccessRequestService;
import org.cdpg.dx.acl.accessRequest.util.AccessRequestAuditLogHelper;
import org.cdpg.dx.acl.policy.util.UserAccessHandler;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.email.SendEmail;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxForbiddenNoAccessException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.RequestType;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class AccessRequestController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(AccessRequestController.class);

  private final AccessRequestService accessRequestService;
  private final PostgresService postgresService;
  private final AuditingHandler auditingHandler;
  private final DataBrokerService dataBrokerService;
  private final KeycloakUserService keycloakUserService;
  private final URNGenerator urnGenerator;
  private final String emailExchange;
  private final String emailRoutingKey;

  public AccessRequestController(
      AccessRequestService accessRequestService,
      AuditingHandler auditingHandler,
      DataBrokerService dataBrokerService,
      URNGenerator urnGenerator,
      PostgresService postgresService,
      KeycloakUserService keycloakUserService,
      String emailExchange,
      String emailRoutingKey) {
    this.accessRequestService = accessRequestService;
    this.auditingHandler = auditingHandler;
    this.dataBrokerService = dataBrokerService;
    this.urnGenerator = urnGenerator;
    this.postgresService = postgresService;
    this.keycloakUserService = keycloakUserService;
    this.emailExchange = emailExchange;
    this.emailRoutingKey = emailRoutingKey;
  }

  private static LocalDateTime parseAndValidateFutureTime(String timeString) {
    if (timeString == null || timeString.isBlank()) {
      LOGGER.warn("expiryAt not provided, defaulting to one year from now.");
      return LocalDateTime.now().plusYears(1);
    }

    try {
      LocalDateTime parsedTime = LocalDateTime.parse(timeString);
      LOGGER.info(
          "Parsed time: {}, isFuture: {}", parsedTime, parsedTime.isAfter(LocalDateTime.now()));
      if (parsedTime.isAfter(LocalDateTime.now())) {
        return parsedTime;
      } else {
        throw new DxValidationException("expiryAt must be a future time");
      }
    } catch (DateTimeParseException e) {
      throw new DxValidationException("expiryAt has invalid format, expected ISO format");
    }
  }

  @Override
  public void register(RouterBuilder builder) {
    Handler<RoutingContext> cosAdminAccessHandler = AuthorizationHandler.forRoles(DxRole.COS_ADMIN);
    Handler<RoutingContext> orgAdminAccessHandler = AuthorizationHandler.forRoles(DxRole.ORG_ADMIN);
    Handler<RoutingContext> providerAndOrgAdminAccessHandler =
        AuthorizationHandler.forRoles(DxRole.PROVIDER, DxRole.ORG_ADMIN);
    UserAccessHandler userAccessHandler = new UserAccessHandler(postgresService, keycloakUserService);

    builder
        .operation(CREATE_ACCESS_REQUEST_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(this::createAccessRequestHandler);

    builder
        .operation(GET_ACCESS_REQUEST_CONSUMER_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(this::getConsumerAccessRequestHandler);

    builder
        .operation(WITHDRAW_ACCESS_REQUEST_API_FOR_CONSUMER)
        .handler(auditingHandler::handleApiAudit)
        .handler(AuthorizationHandler.forRoles(DxRole.CONSUMER))
        .handler(this::updateAccessRequestHandlerForConumser);

    builder
        .operation(GET_ACCESS_REQUEST_FOR_ORG_ADMIN_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAdminAccessHandler)
        .handler(this::getOrganizationAccessRequestHandler);

    builder
        .operation(GET_ACCESS_REQUEST_FOR_COS_ADMIN_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(cosAdminAccessHandler)
        .handler(this::getPlatformAccessRequestHandler);

    builder
        .operation(GET_ACCESS_REQUEST_PROVIDER_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(providerAndOrgAdminAccessHandler)
        .handler(this::getAccessRequestHandler);

    builder
        .operation(UPDATE_ACCESS_REQUEST_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(userAccessHandler)
        .handler(providerAndOrgAdminAccessHandler)
        .handler(this::updateAccessRequestHandler);

    builder
        .operation(CHECK_ACCESS_REQUEST_API)
        .handler(auditingHandler::handleApiAudit)
        .handler(this::checkAccessRequestHandler);
  }

  private void updateAccessRequestHandlerForConumser(RoutingContext routingContext) {
    LOGGER.info("Handling deleteAccessRequest request...");

    UUID userId = UUID.fromString(routingContext.user().subject());
    UUID requestId = RequestHelper.getPathParamAsUUID(routingContext, "id");

    accessRequestService
        .updateAccessRequestForConsumer(userId, requestId)
        .onSuccess(
            accessRequestDto -> {

              //          Future<Void> future =
              //            emailComposer.sendEmailForUpdateAccessRequest(accessRequestDto, status);

              UserActivityAuditLogBuilder auditLog =
                  AccessRequestAuditLogHelper.buildAudit(
                      routingContext, accessRequestDto, AccessRequestAuditOperation.WITHDRAW);
              CpRoutingContextHelper.setAuditingLogV2(routingContext, auditLog);
              ResponseBuilder.sendSuccess(
                  routingContext, "Request updated successfully", urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error("Error withdrawing access request: {}", err.getMessage(), err);
              routingContext.fail(err);
            });
  }

  private void getAccessRequestHandler(RoutingContext ctx) {
    LOGGER.info("Handling getAccessRequest request...");
    User user = ctx.user();

    Map<String, String> allowedFilters =
        Map.of("requestStatus", DB_STATUS, "assetType", DB_ASSET_TYPE);
    Map<String, Object> additionalFilters = Map.of("provider_id", user.subject());
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

    accessRequestService
        .listAccessRequestForProvider(request)
        .compose(accessRequestService::enrichAccessRequestsWithItemDetails)
        .onSuccess(
            pagedResult -> {
              LOGGER.info(
                  "Successfully fetched access requests for provider user: {}", user.subject());
              ResponseBuilder.sendSuccess(
                  ctx,
                  pagedResult.data().stream()
                      .map(AccessRequestDto::toJson)
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

  private void getOrganizationAccessRequestHandler(RoutingContext ctx) {
    LOGGER.info("Handling getOrganizationAccessRequestHandler request...");
    User user = ctx.user();

    String organizationId = RoutingContextHelper.fromPrincipal(ctx).organisationId();
    Map<String, String> allowedFilters =
        Map.of("requestStatus", DB_STATUS, "assetType", DB_ASSET_TYPE);
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

    accessRequestService
        .listAccessRequestForProvider(request)
        .compose(accessRequestService::enrichAccessRequestsWithItemDetails)
        .onSuccess(
            pagedResult -> {
              LOGGER.info(
                  "Successfully fetched access requests for org admin user: {}", user.subject());
              ResponseBuilder.sendSuccess(
                  ctx,
                  pagedResult.data().stream()
                      .map(AccessRequestDto::toJson)
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

  private void getPlatformAccessRequestHandler(RoutingContext ctx) {
    LOGGER.info("Handling getPlatformAccessRequestHandler request...");
    User user = ctx.user();

    Map<String, String> allowedFilters =
        Map.of("requestStatus", DB_STATUS, "assetType", DB_ASSET_TYPE);
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

    accessRequestService
        .listAccessRequestForProvider(request)
        .compose(accessRequestService::enrichAccessRequestsWithItemDetails)
        .onSuccess(
            pagedResult -> {
              LOGGER.info(
                  "Successfully fetched access requests for cos admin user: {}", user.subject());
              ResponseBuilder.sendSuccess(
                  ctx,
                  pagedResult.data().stream()
                      .map(AccessRequestDto::toJson)
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

  private void checkAccessRequestHandler(RoutingContext ctx) {
    LOGGER.info("Handling checkAccessRequest request...");
    JsonObject body = ctx.body().asJsonObject();
    String itemId = body.getString("itemId");
    UUID consumerId = UUID.fromString(ctx.user().subject());

    accessRequestService
        .checkAccessRequest(consumerId, itemId)
        .onSuccess(
            hasAccess -> {
              if (hasAccess) {
                ResponseBuilder.sendSuccess(
                    ctx, "User has access to the given asset!", urnGenerator);
              } else {
                ctx.fail(new DxForbiddenNoAccessException("User has access to the given asset!"));
              }
            })
        .onFailure(
            err -> {
              LOGGER.error("Error checking access request: {}", err.getMessage(), err);
              ctx.fail(err);
            });
  }

  private void updateAccessRequestHandler(RoutingContext ctx) {
    LOGGER.info("Handling updateAccessRequest request...");
    JsonObject body = ctx.body().asJsonObject();
    UUID requestId = UUID.fromString(body.getString("requestId"));
    Status status = Status.fromString(body.getString("status"));
    JsonObject constraints = body.getJsonObject("constraints");
    String feedbackToConsumer = body.getString("feedbackToConsumer", "");
    String providerComment = body.getString("providerComment", "");
    UUID providerId = UUID.fromString(ctx.user().subject());

    keycloakUserService
        .getUserById(providerId)
        .onFailure(
            err -> {
              LOGGER.error("Failed to fetch provider from Keycloak", err);
              ctx.fail(new DxForbiddenException("Invalid provider"));
            })
        .onSuccess(
            provider -> {
              UUID providerOrganizationId =
                  provider.organisationId() != null
                      ? UUID.fromString(provider.organisationId())
                      : null;
              boolean isUserOrgAdmin = provider.roles().contains(DxRole.ORG_ADMIN.getRole());

              if (status == Status.GRANTED) {
                LocalDateTime expiryAt = parseAndValidateFutureTime(body.getString("expiryAt"));

                accessRequestService
                    .approveAccessRequest(
                        providerId,
                        requestId,
                        expiryAt,
                        providerOrganizationId,
                        isUserOrgAdmin,
                        constraints,
                        providerComment,
                        feedbackToConsumer)
                    .onSuccess(
                        accessRequestDto -> {
                          UserActivityAuditLogBuilder auditLog =
                              AccessRequestAuditLogHelper.buildAudit(
                                  ctx, accessRequestDto, AccessRequestAuditOperation.GRANT);
                          CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);
                          ResponseBuilder.sendSuccess(
                              ctx, "Request updated successfully", urnGenerator);
                          JsonObject jsonObject =
                              new SendEmail(
                                      accessRequestDto.getConsumerId(),
                                      "PATH",
                                      "templates/AssetRequestApprovedEmailTemplate.html",
                                      null,
                                      accessRequestDto.getAssetType(),
                                      accessRequestDto.getItemId(),
                                      accessRequestDto.getShortDescription(),
                                      false,
                                      status.getStatus(),
                                      accessRequestDto.getAssetName())
                                  .toJson();
                          Future<Void> future =
                              dataBrokerService.publishMessageInternal(
                                  jsonObject, emailExchange, emailRoutingKey);
                        })
                    .onFailure(
                        err -> {
                          LOGGER.error("Error updating access request: {}", err.getMessage(), err);
                          ctx.fail(err);
                        });
              } else {
                // pass providerComment and feedbackToConsumer to be stored
                accessRequestService
                    .rejectAccessRequest(
                        providerId,
                        requestId,
                        providerOrganizationId,
                        isUserOrgAdmin,
                        providerComment,
                        feedbackToConsumer)
                    .onSuccess(
                        accessRequestDto -> {
                          UserActivityAuditLogBuilder auditLog =
                              AccessRequestAuditLogHelper.buildAudit(
                                  ctx, accessRequestDto, AccessRequestAuditOperation.REJECT);
                          CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);
                          // RoutingContextHelper.setAuditingLog(ctx, auditLog);
                          ResponseBuilder.sendSuccess(
                              ctx, "Request updated successfully", urnGenerator);
                          JsonObject jsonObject =
                              new SendEmail(
                                      accessRequestDto.getConsumerId(),
                                      "PATH",
                                      "templates/AssetRequestApprovedEmailTemplate.html",
                                      null,
                                      accessRequestDto.getAssetType(),
                                      accessRequestDto.getItemId(),
                                      accessRequestDto.getShortDescription(),
                                      false,
                                      status.getStatus(),
                                      accessRequestDto.getAssetName())
                                  .toJson();
                          Future<Void> future =
                              dataBrokerService.publishMessageInternal(
                                  jsonObject, emailExchange, emailRoutingKey);
                        })
                    .onFailure(
                        err -> {
                          LOGGER.error("Error rejecting access request: {}", err.getMessage(), err);
                          ctx.fail(err);
                        });
              }
            });
  }

  private void createAccessRequestHandler(RoutingContext ctx) {
    LOGGER.info("Handling createAccessRequest request...");
    JsonObject body = ctx.body().asJsonObject();
    UUID itemId = UUID.fromString(body.getString("itemId"));
    RequestType requestType = RequestType.valueOf(body.getString("requestType"));
    JsonObject additionalInfo = body.getJsonObject("additionalInfo");
    JsonObject constraints = body.getJsonObject("constraints");
    DxUser consumer;
    try {
      consumer = RoutingContextHelper.fromPrincipal(ctx);
    } catch (Exception e) {
      LOGGER.error("Error extracting user from token: {}", e.getMessage(), e);
      ctx.fail(new DxForbiddenException("Invalid user"));
      return;
    }

    accessRequestService
        .createAccessRequest(consumer, itemId, requestType, additionalInfo, constraints)
        .onSuccess(
            accessRequestDto -> {
              UserActivityAuditLogBuilder auditLog =
                  AccessRequestAuditLogHelper.buildAudit(
                      ctx, accessRequestDto, AccessRequestAuditOperation.REQUEST);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLog);
              // RoutingContextHelper.setAuditingLog(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, "Request inserted successfully!", urnGenerator);
              JsonObject jsonObject =
                  new SendEmail(
                          accessRequestDto.getConsumerId(),
                          "PATH",
                          "templates/AssetRequestEmailTemplate.html",
                          accessRequestDto.getProviderId(),
                          accessRequestDto.getAssetType(),
                          accessRequestDto.getItemId(),
                          accessRequestDto.getShortDescription(),
                          true,
                          null,
                          accessRequestDto.getAssetName())
                      .toJson();
              Future<Void> future =
                  dataBrokerService.publishMessageInternal(
                      jsonObject, emailExchange, emailRoutingKey);
            })
        .onFailure(
            err -> {
              LOGGER.error("Error creating access request: {}", err.getMessage(), err);
              ctx.fail(err);
            });
  }

  private void getConsumerAccessRequestHandler(RoutingContext ctx) {
    LOGGER.info("Handling getAccessRequest request...");
    User user = ctx.user();

    Map<String, String> allowedFilters =
        Map.of("requestStatus", DB_STATUS, "assetType", DB_ASSET_TYPE);
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

    accessRequestService
        .listAccessRequestForConsumer(request)
        .compose(accessRequestService::enrichAccessRequestsWithItemDetails)
        .onSuccess(
            pagedResult -> {
              LOGGER.info("Successfully fetched access requests for user: {}", user.subject());
              ResponseBuilder.sendSuccess(
                  ctx,
                  pagedResult.data().stream()
                      .map(AccessRequestDto::toJson) // call toJson on each object
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
}
